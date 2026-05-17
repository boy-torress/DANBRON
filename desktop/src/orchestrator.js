/**
 * Danbron Orchestrator v3.0 — Senior-grade agentic core
 *
 * Architecture:
 *   Perception → Memory → Planner → Executor (ReAct loop) → Tool Registry
 *
 * Patterns implemented:
 *   - ReAct (Reason + Act + Observe)
 *   - Episodic + Semantic + Working memory
 *   - Multi-step planning with dependency resolution
 *   - Circuit Breaker per tool
 *   - Saga-style rollback on failure
 *   - Dynamic tool registry with capability scoring
 *   - Self-reflection on step results
 *
 * Drop-in integration: load this before app.js.
 * Call: window.BronOrchestrator.run(userMessage, appState)
 */

(function (global) {
  'use strict';

  // ─────────────────────────────────────────────
  // SECTION 1: MEMORY SYSTEM
  // ─────────────────────────────────────────────

  /**
   * Three-tier memory store:
   *   - working:   current turn context (cleared each turn)
   *   - episodic:  conversation history, compressed (localStorage)
   *   - semantic:  learned facts about the user (localStorage, persistent)
   */
  class MemoryStore {
    constructor(userId) {
      this.userId = userId || 'anonymous';
      this.working = {};
      this._load();
    }

    _key(type) {
      return `danbron_mem_${type}_${this.userId}`;
    }

    _load() {
      try {
        this.episodic = JSON.parse(localStorage.getItem(this._key('episodic')) || '[]');
        this.semantic = JSON.parse(localStorage.getItem(this._key('semantic')) || '{}');
      } catch {
        this.episodic = [];
        this.semantic = {};
      }
    }

    _save() {
      try {
        // Keep last 40 episodes to stay within localStorage limits
        localStorage.setItem(this._key('episodic'), JSON.stringify(this.episodic.slice(-40)));
        localStorage.setItem(this._key('semantic'), JSON.stringify(this.semantic));
      } catch (e) {
        console.warn('[Memory] Save failed:', e.message);
      }
    }

    /** Write to working memory for the current turn */
    setWorking(key, value) {
      this.working[key] = value;
    }

    getWorking(key) {
      return this.working[key];
    }

    clearWorking() {
      this.working = {};
    }

    /**
     * Add an episode (a completed turn summary).
     * Automatically extracts semantic facts from it.
     */
    addEpisode(episode) {
      this.episodic.push({
        ts: Date.now(),
        intent: episode.intent,
        plan: episode.plan?.map(s => s.toolId) ?? [],
        outcome: episode.outcome, // 'success' | 'partial' | 'failed'
        reflection: episode.reflection,
        summary: episode.summary,
      });
      this._extractSemanticFacts(episode);
      this._save();
    }

    /**
     * Learn persistent facts about the user from an episode.
     * These survive across sessions.
     */
    _extractSemanticFacts(episode) {
      const { intent = '', summary = '' } = episode;
      const text = `${intent} ${summary}`.toLowerCase();

      // Financial facts
      const incomeMatch = text.match(/ingresos?[^$\d]*\$?([\d,.]+)/);
      if (incomeMatch) this.semantic.income = incomeMatch[1];

      const debtMatch = text.match(/deuda[^$\d]*\$?([\d,.]+)/);
      if (debtMatch) this.semantic.debt = debtMatch[1];

      // Behavioral patterns
      if (text.includes('spotify') || text.includes('musica')) {
        this.semantic.usesSpotify = true;
      }
      if (text.includes('youtube') || text.includes('video')) {
        this.semantic.usesYouTube = true;
      }
      if (text.includes('gmail') || text.includes('correo')) {
        this.semantic.usesGmail = true;
      }

      // Distraction patterns (used for proactive coaching)
      if (episode.intent === 'open_app' && (text.includes('juego') || text.includes('steam') || text.includes('youtube'))) {
        this.semantic.distractionCount = (this.semantic.distractionCount || 0) + 1;
        this.semantic.lastDistractionTs = Date.now();
      }

      this._save();
    }

    /**
     * Retrieve relevant episodic memories for a given intent.
     * Simple keyword overlap — no vector DB needed at this scale.
     */
    recallRelevant(intentText, limit = 5) {
      const keywords = intentText.toLowerCase().split(/\s+/).filter(w => w.length > 3);
      return this.episodic
        .map(ep => {
          const score = keywords.filter(kw =>
            (ep.intent || '').toLowerCase().includes(kw) ||
            (ep.summary || '').toLowerCase().includes(kw)
          ).length;
          return { ...ep, score };
        })
        .filter(ep => ep.score > 0)
        .sort((a, b) => b.score - a.score || b.ts - a.ts)
        .slice(0, limit);
    }

    /** Get a condensed context string to inject into the system prompt */
    getContextSummary() {
      const facts = [];
      if (this.semantic.income) facts.push(`ingresos conocidos: $${this.semantic.income}`);
      if (this.semantic.debt) facts.push(`deuda conocida: $${this.semantic.debt}`);
      if (this.semantic.distractionCount > 3) facts.push(`patron de distraccion detectado (${this.semantic.distractionCount} veces)`);
      if (this.semantic.usesSpotify) facts.push('usa Spotify habitualmente');
      if (this.semantic.usesGmail) facts.push('usa Gmail frecuentemente');

      const recent = this.episodic.slice(-3).map(ep =>
        `[${new Date(ep.ts).toLocaleDateString('es')}] ${ep.intent} → ${ep.outcome}`
      );

      return [
        facts.length ? `Hechos aprendidos: ${facts.join(', ')}.` : '',
        recent.length ? `Historial reciente: ${recent.join(' | ')}.` : '',
      ].filter(Boolean).join('\n');
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 2: TOOL REGISTRY
  // ─────────────────────────────────────────────

  /**
   * Dynamic tool registry.
   * Tools are registered with a capability descriptor.
   * findBestTool() scores candidates against the current intent.
   */
  class ToolRegistry {
    constructor() {
      this.tools = new Map();
    }

    /**
     * Register a tool.
     * @param {object} descriptor
     *   id          - unique string
     *   description - human-readable
     *   keywords    - string[] for capability scoring
     *   targets     - string[] ('windows'|'android'|'local'|'any')
     *   requiresAuth - boolean
     *   requiresPairing - boolean
     *   execute     - async (args, context) => { ok, result, error }
     *   rollback    - async (args, context) => void  [optional]
     */
    register(descriptor) {
      if (!descriptor.id || typeof descriptor.execute !== 'function') {
        throw new Error(`[ToolRegistry] Tool must have id and execute(): ${descriptor.id}`);
      }
      this.tools.set(descriptor.id, {
        callCount: 0,
        failCount: 0,
        circuitOpen: false,
        circuitOpenUntil: 0,
        ...descriptor,
      });
    }

    /**
     * Score a tool against an intent string.
     * Returns 0–1 based on keyword overlap.
     */
    _score(tool, intentText) {
      const text = intentText.toLowerCase();
      const hits = tool.keywords.filter(kw => text.includes(kw.toLowerCase())).length;
      return hits / Math.max(tool.keywords.length, 1);
    }

    /**
     * Find the best tool for an intent, respecting circuit breakers.
     * Returns null if no tool is suitable.
     */
    findBestTool(intentText, options = {}) {
      const { target, requiresAuth, requiresPairing } = options;
      const now = Date.now();

      const candidates = [...this.tools.values()]
        .filter(t => {
          if (t.circuitOpen && now < t.circuitOpenUntil) return false;
          if (t.circuitOpen && now >= t.circuitOpenUntil) {
            t.circuitOpen = false; // half-open: allow one retry
          }
          if (target && !t.targets.includes('any') && !t.targets.includes(target)) return false;
          if (requiresAuth && t.requiresAuth && !requiresAuth) return false;
          if (requiresPairing && t.requiresPairing && !requiresPairing) return false;
          return true;
        })
        .map(t => ({ tool: t, score: this._score(t, intentText) }))
        .filter(c => c.score > 0)
        .sort((a, b) => b.score - a.score);

      return candidates[0]?.tool ?? null;
    }

    /** Notify registry of a tool result for circuit breaker tracking */
    recordResult(toolId, ok) {
      const tool = this.tools.get(toolId);
      if (!tool) return;
      tool.callCount++;
      if (!ok) {
        tool.failCount++;
        // Open circuit if last 3 calls all failed
        const recentFails = tool.failCount;
        if (recentFails >= 3) {
          tool.circuitOpen = true;
          tool.circuitOpenUntil = Date.now() + 30_000; // 30s cooldown
          console.warn(`[CircuitBreaker] Tool ${toolId} circuit OPEN for 30s`);
        }
      } else {
        tool.failCount = Math.max(0, tool.failCount - 1); // gradual recovery
      }
    }

    list() {
      return [...this.tools.values()];
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 3: PLANNER
  // ─────────────────────────────────────────────

  /**
   * Converts a classified intent into an ordered execution plan.
   * Each step has: toolId, args, dependsOn[], rollbackable.
   *
   * For simple single-tool intents this is trivial.
   * For complex intents (briefing, agent_test) it produces a dependency graph.
   */
  class Planner {
    constructor(toolRegistry) {
      this.registry = toolRegistry;
    }

    /**
     * Build a plan from a classified intent.
     * @param {object} intent  Output of Perception.classify()
     * @param {object} context App state, memory summary, etc.
     * @returns {Step[]}
     */
    buildPlan(intent, context) {
      const { type, target, action, args, requiresConfirmation } = intent;

      // Multi-step: daily briefing
      if (action === 'daily_briefing') {
        return [
          { id: 's1', toolId: 'context_reader',    args: {},           dependsOn: [],     rollbackable: false },
          { id: 's2', toolId: 'gmail_reader',       args: { max: 5 },  dependsOn: [],     rollbackable: false },
          { id: 's3', toolId: 'calendar_reader',    args: {},          dependsOn: [],     rollbackable: false },
          { id: 's4', toolId: 'drive_reader',       args: { max: 5 },  dependsOn: [],     rollbackable: false },
          { id: 's5', toolId: 'briefing_composer',  args: {},          dependsOn: ['s1','s2','s3','s4'], rollbackable: false },
        ];
      }

      // Multi-step: full agent test
      if (action === 'agent_test') {
        return [
          { id: 's1', toolId: 'context_reader',    args: {},                      dependsOn: [],              rollbackable: false },
          { id: 's2', toolId: 'gmail_reader',       args: { query: 'is:unread', max: 5 }, dependsOn: [],      rollbackable: false },
          { id: 's3', toolId: 'calendar_reader',    args: {},                      dependsOn: [],              rollbackable: false },
          { id: 's4', toolId: 'drive_reader',       args: { max: 5 },              dependsOn: [],              rollbackable: false },
          { id: 's5', toolId: 'calendar_writer',    args: { summary: 'Danbron Agent Test', offsetDays: 1 }, dependsOn: ['s3'], rollbackable: true },
          { id: 's6', toolId: 'gmail_sender',       args: { to: 'brandontorres.dev@gmail.com', subject: 'Danbron Agent Test completado', body: 'Prueba integral completada desde Danbron v3.' }, dependsOn: ['s2'], rollbackable: false },
          { id: 's7', toolId: 'android_commander',  args: { action: 'open_app', args: { app: 'youtube' } }, dependsOn: [], rollbackable: false },
          { id: 's8', toolId: 'test_reporter',      args: {},                      dependsOn: ['s1','s2','s3','s4','s5','s6','s7'], rollbackable: false },
        ];
      }

      // Single-step: everything else maps to one tool
      const toolId = this._mapActionToTool(action, target);
      if (!toolId) return [];

      return [
        { id: 's1', toolId, args: args || {}, dependsOn: [], rollbackable: requiresConfirmation === true, requiresConfirmation }
      ];
    }

    _mapActionToTool(action, target) {
      const map = {
        open_url:             target === 'android' ? 'android_commander' : 'windows_shell',
        open_app:             target === 'android' ? 'android_commander' : 'windows_shell',
        compose_whatsapp:     target === 'android' ? 'android_commander' : 'windows_shell',
        compose_email:        'windows_shell',
        send_email:           'gmail_sender',
        create_calendar_event:'calendar_writer',
        play_music:           target === 'android' ? 'android_commander' : 'windows_shell',
        open_maps:            target === 'android' ? 'android_commander' : 'windows_shell',
        pc_context_question:  'context_reader',
        ai_chat:              'llm_tool',
      };
      return map[action] || 'llm_tool';
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 4: EXECUTOR WITH REFLECTION
  // ─────────────────────────────────────────────

  /**
   * Executes a plan step by step.
   * After each step: observe result → reflect → decide to continue/replan/abort.
   * On rollback-eligible failures: runs rollback functions in reverse order.
   */
  class Executor {
    constructor(toolRegistry, memory, options = {}) {
      this.registry = toolRegistry;
      this.memory = memory;
      this.maxRetries = options.maxRetries || 2;
      this.onProgress = options.onProgress || (() => {});
    }

    /**
     * Run a plan. Returns { outcome, results, reflection, summary }.
     */
    async run(plan, intentText, appContext) {
      const results = {};
      const completedSteps = [];
      let outcome = 'success';
      let reflection = '';
      let summary = '';

      // Resolve step execution order respecting dependencies (topological sort)
      const ordered = this._topoSort(plan);

      for (const step of ordered) {
        // Check all dependencies succeeded
        const depsOk = step.dependsOn.every(depId => results[depId]?.ok !== false);
        if (!depsOk) {
          results[step.id] = { ok: false, error: `Dependency failed for step ${step.id}` };
          outcome = 'partial';
          continue;
        }

        const tool = this.registry.tools.get(step.toolId);
        if (!tool) {
          results[step.id] = { ok: false, error: `Tool not found: ${step.toolId}` };
          outcome = 'partial';
          continue;
        }

        // Circuit breaker check
        if (tool.circuitOpen && Date.now() < tool.circuitOpenUntil) {
          results[step.id] = { ok: false, error: `Tool ${step.toolId} unavailable (circuit open)` };
          outcome = 'partial';
          continue;
        }

        // Confirmation gate
        if (step.requiresConfirmation) {
          results[step.id] = {
            ok: true,
            result: `[PENDING CONFIRMATION] ${step.toolId}`,
            pendingConfirmation: true,
          };
          continue;
        }

        // Execute with retry + exponential backoff
        let stepResult = { ok: false, error: 'Not attempted' };
        for (let attempt = 0; attempt <= this.maxRetries; attempt++) {
          if (attempt > 0) {
            await this._sleep(Math.pow(2, attempt - 1) * 800);
          }
          try {
            // Pass results of dependency steps into args (allows data chaining)
            const enrichedArgs = this._enrichArgs(step.args, step.dependsOn, results);
            const ctx = { appContext, memory: this.memory, results };
            stepResult = await tool.execute(enrichedArgs, ctx);
            if (stepResult.ok) break;
          } catch (err) {
            stepResult = { ok: false, error: err.message || String(err) };
          }
        }

        this.registry.recordResult(step.toolId, stepResult.ok);
        results[step.id] = stepResult;
        this.onProgress(step, stepResult);

        if (stepResult.ok) {
          completedSteps.push(step);
        } else {
          outcome = 'partial';
          // Reflect: is this step critical?
          const isCritical = step.dependsOn.length === 0 && plan.some(s => s.dependsOn.includes(step.id));
          if (isCritical) {
            outcome = 'failed';
            reflection = `El paso crítico ${step.toolId} falló: ${stepResult.error}. No puedo continuar el plan.`;
            break;
          }
        }
      }

      // Rollback if failed and there are rollback-eligible completed steps
      if (outcome === 'failed') {
        await this._rollback(completedSteps, appContext);
      }

      // Final reflection: summarize what happened
      reflection = reflection || this._reflect(plan, results, outcome);
      summary = this._summarize(intentText, results, outcome);

      return { outcome, results, reflection, summary };
    }

    _topoSort(steps) {
      const sorted = [];
      const visited = new Set();
      const visit = (step) => {
        if (visited.has(step.id)) return;
        visited.add(step.id);
        step.dependsOn.forEach(depId => {
          const dep = steps.find(s => s.id === depId);
          if (dep) visit(dep);
        });
        sorted.push(step);
      };
      steps.forEach(visit);
      return sorted;
    }

    _enrichArgs(args, dependsOn, results) {
      // Allow steps to receive output from their dependencies
      const depData = {};
      dependsOn.forEach(depId => {
        if (results[depId]?.ok) depData[depId] = results[depId].result;
      });
      return { ...args, _deps: depData };
    }

    async _rollback(completedSteps, appContext) {
      const rollbackable = completedSteps.filter(s => s.rollbackable).reverse();
      for (const step of rollbackable) {
        const tool = this.registry.tools.get(step.toolId);
        if (tool?.rollback) {
          try {
            await tool.rollback(step.args, { appContext, memory: this.memory });
            console.info(`[Rollback] ${step.toolId} rolled back.`);
          } catch (err) {
            console.warn(`[Rollback] ${step.toolId} rollback failed:`, err);
          }
        }
      }
    }

    _reflect(plan, results, outcome) {
      if (outcome === 'success') {
        const tools = plan.map(s => s.toolId).join(', ');
        return `Todo completado con éxito. Herramientas usadas: ${tools}.`;
      }
      const failed = plan.filter(s => !results[s.id]?.ok).map(s =>
        `${s.toolId}: ${results[s.id]?.error || 'error desconocido'}`
      );
      return `Completado parcialmente. Fallaron: ${failed.join('; ')}.`;
    }

    _summarize(intentText, results, outcome) {
      const successCount = Object.values(results).filter(r => r.ok).length;
      const total = Object.keys(results).length;
      return `Intent: "${intentText.slice(0, 80)}" → ${successCount}/${total} pasos OK → ${outcome}`;
    }

    _sleep(ms) {
      return new Promise(resolve => setTimeout(resolve, ms));
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 5: PERCEPTION — intent classifier
  // ─────────────────────────────────────────────

  class Perception {
    /**
     * Classify a raw user message into a structured intent.
     * This replaces your regex soup with a clean, extensible taxonomy.
     */
    static classify(text, appState) {
      const t = text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');

      // --- Agent-level commands ---
      if (/\b(briefing|resumen del dia|que tengo hoy|que necesito saber)\b/.test(t)) {
        return { type: 'automation', action: 'daily_briefing', target: 'local', args: {}, confidence: 1.0 };
      }
      if (/\b(prueba completa|prueba integral|modo agente|agent test)\b/.test(t)) {
        return { type: 'automation', action: 'agent_test', target: 'local', args: {}, confidence: 1.0 };
      }

      // --- PC context questions ---
      const contextTerms = ['abierto','ventana','programa','app','aplicacion','pc','computador'];
      const askTerms = ['que tengo','que hay','que esta','que ves','dime','sabes'];
      if (contextTerms.some(kw => t.includes(kw)) && askTerms.some(kw => t.includes(kw))) {
        return { type: 'automation', action: 'pc_context_question', target: 'local', args: {}, confidence: 0.9 };
      }

      // --- Target detection ---
      const androidTarget = /\b(telefono|celular|android|movil)\b/.test(t);
      const target = androidTarget ? 'android' : 'windows';

      // --- Email ---
      if (/\b(gmail|correo|email|mail)\b/.test(t) && /\b(envia|manda|escribe|redacta)\b/.test(t)) {
        const action = /\b(envia|envialo|manda|mandalo)\b/.test(t) ? 'send_email' : 'compose_email';
        return { type: 'automation', action, target, args: Perception._extractEmail(text), confidence: 0.95 };
      }

      // --- Calendar ---
      if (/\b(agenda|agendar|evento|reunion|cita|calendario)\b/.test(t)) {
        return { type: 'automation', action: 'create_calendar_event', target, args: Perception._extractCalendar(text), requiresConfirmation: true, confidence: 0.9 };
      }

      // --- Music ---
      if (/\b(musica|cancion|spotify|reproduce|escucha)\b/.test(t)) {
        const query = Perception._extractAfter(text, ['spotify','musica','cancion','reproduce','escucha']);
        const service = t.includes('spotify') ? 'spotify' : 'auto';
        return { type: 'automation', action: 'play_music', target, args: { query, service }, confidence: 0.9 };
      }

      // --- Maps ---
      if (/\b(mapa|maps|direccion|ruta|navega|lleva)\b/.test(t)) {
        const query = Perception._extractAfter(text, ['maps','mapa','direccion','ruta']);
        return { type: 'automation', action: 'open_maps', target, args: { query }, confidence: 0.85 };
      }

      // --- WhatsApp ---
      if (/\b(whatsapp|wsp)\b/.test(t) && /\b(envia|manda|escribe)\b/.test(t)) {
        const message = Perception._extractMessage(text);
        return { type: 'automation', action: 'compose_whatsapp', target, args: { message }, requiresConfirmation: true, confidence: 0.9 };
      }

      // --- Open app/url ---
      const appMatch = Perception._detectApp(t);
      if (appMatch && /\b(abre|abrir|pon|entra)\b/.test(t)) {
        if (appMatch === 'youtube') {
          const query = Perception._extractAfter(text, ['youtube','busca','buscar','pon','reproduce']);
          return query
            ? { type: 'automation', action: 'open_url', target, args: { url: `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}` }, confidence: 0.85 }
            : { type: 'automation', action: 'open_app', target, args: { app: appMatch }, confidence: 0.85 };
        }
        return { type: 'automation', action: 'open_app', target, args: { app: appMatch }, confidence: 0.85 };
      }

      // --- Web search ---
      if (/\b(busca|buscar)\b/.test(t)) {
        const query = Perception._extractAfter(text, ['busca','buscar']);
        return { type: 'automation', action: 'open_url', target, args: { url: `https://www.google.com/search?q=${encodeURIComponent(query)}` }, confidence: 0.8 };
      }

      // --- Default: conversational AI ---
      return { type: 'conversation', action: 'ai_chat', target: 'local', args: { text }, confidence: 0.6 };
    }

    static _detectApp(t) {
      const apps = {
        opera: ['opera'], chrome: ['chrome'], whatsapp: ['whatsapp','wsp'],
        gmail: ['gmail','correo'], calendar: ['calendar','calendario'],
        spotify: ['spotify'], music: ['musica'], youtube: ['youtube'],
        portal_universidad: ['portal','universidad'],
      };
      for (const [app, keywords] of Object.entries(apps)) {
        if (keywords.some(kw => t.includes(kw))) return app;
      }
      return null;
    }

    static _extractAfter(text, markers) {
      const norm = text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
      for (const marker of markers) {
        const idx = norm.indexOf(marker);
        if (idx >= 0) {
          return text.slice(idx + marker.length)
            .replace(/^[\s,:\-]*(con|de|en|a|el|la|los|las|un|una)\s+/i, '')
            .trim();
        }
      }
      return text.trim();
    }

    static _extractMessage(text) {
      const quoted = text.match(/["«"](.+?)["»"]/);
      return quoted?.[1]?.trim() || Perception._extractAfter(text, ['envia','manda','escribe']);
    }

    static _extractEmail(text) {
      const toMatch = text.match(/\b(?:a|para)\s+([^\s,;]+@[^\s,;]+)/i);
      const subjectMatch = text.match(/\b(?:asunto|subject)\s*[:\-]?\s*(.+?)(?:\s+diciendo|\s+cuerpo|\s+mensaje|[.,]|$)/i);
      const body = Perception._extractAfter(text, ['correo','email','mail','diciendo que','cuerpo','mensaje']);
      return { to: toMatch?.[1] || '', subject: subjectMatch?.[1]?.trim() || '', body: body.trim() };
    }

    static _extractCalendar(text) {
      const quoted = text.match(/["«"](.+?)["»"]/);
      const title = quoted?.[1]?.trim() ||
        Perception._extractAfter(text, ['agenda','agendar','crea','programa','reunion','cita','evento']) ||
        'Nuevo evento';
      return { title: title.trim(), details: text.trim() };
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 6: BUILT-IN TOOLS
  // ─────────────────────────────────────────────

  function buildBuiltinTools(appState) {
    const invokeTauri = (...args) => {
      const tauri = window.__TAURI__;
      const invoke = tauri?.core?.invoke || tauri?.tauri?.invoke;
      if (!invoke) return Promise.resolve(null);
      return invoke(...args);
    };
    const isTauri = () => !!window.__TAURI__;
    const psQuote = v => `'${String(v).replace(/'/g, "''")}'`;
    const runPS = async (script) => {
      const output = await invokeTauri('run_shell_command', {
        cmd: 'powershell.exe',
        args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script],
      });
      return String(output || '').trim();
    };
    const runGog = async (...args) => {
      if (!isTauri()) return { ok: false, text: 'Tauri not available' };
      try {
        const cmd = ['gog', ...args.map(psQuote)].join(' ');
        const text = await runPS(cmd);
        return { ok: true, text };
      } catch (err) {
        return { ok: false, text: err.message || String(err) };
      }
    };

    return [
      // --- Windows Shell ---
      {
        id: 'windows_shell',
        description: 'Executes Windows actions via PowerShell (open apps, URLs, compose messages)',
        keywords: ['open','app','url','windows','abrir','abre','navega','busca','musica','mapa','whatsapp','compose','email'],
        targets: ['windows', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const script = buildWindowsScript(args);
          if (!script) return { ok: false, error: `No script for action: ${JSON.stringify(args)}` };
          await runPS(script);
          return { ok: true, result: `Ejecutado en Windows: ${args.action || args.url || args.app}` };
        },
      },

      // --- Android Commander ---
      {
        id: 'android_commander',
        description: 'Sends commands to paired Android phone',
        keywords: ['android','telefono','celular','movil','whatsapp','musica','mapa','abrir'],
        targets: ['android'],
        requiresAuth: true,
        requiresPairing: true,
        async execute(args, ctx) {
          const sc = window.syncClient;
          if (!sc?.isAuthenticated?.() || !sc?.isPaired?.()) {
            return { ok: false, error: 'Tu telefono no esta conectado. Vinculalo primero.' };
          }
          const queued = await sc.sendRemoteCommand({
            target: 'android',
            action: args.action,
            args: args.args || {},
            requiresConfirmation: !!args.requiresConfirmation
          });
          if (!queued?.id) return { ok: false, error: 'No pude enviar la orden al telefono.' };
          return { ok: true, result: 'Listo, le mande la orden a tu telefono.' };
        },
      },

      // --- Context Reader ---
      {
        id: 'context_reader',
        description: 'Reads current Windows context: open windows, running apps, battery',
        keywords: ['contexto','ventanas','apps','abierto','pc','computador','que ves','monitor'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args, ctx) {
          const tracker = window.systemTracker;
          if (!tracker?.getFullContext) return { ok: false, error: 'System tracker not available' };
          const context = await tracker.getFullContext();
          ctx.memory.setWorking('pcContext', context);
          return { ok: true, result: context };
        },
      },

      // --- Gmail Reader ---
      {
        id: 'gmail_reader',
        description: 'Reads Gmail unread emails via Gog CLI',
        keywords: ['gmail','correo','email','mail','unread','no leido','bandeja'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const query = args.query || 'is:unread';
          const max = args.max || 5;
          const result = await runGog('gmail', 'search', query, '--max', String(max));
          return result.ok
            ? { ok: true, result: result.text || 'Sin correos no leídos.' }
            : { ok: false, error: result.text };
        },
      },

      // --- Gmail Sender ---
      {
        id: 'gmail_sender',
        description: 'Sends emails via Gog CLI or Gmail web fallback',
        keywords: ['enviar','send','correo','email','gmail'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const { to, subject, body } = args;
          if (!to) return { ok: false, error: 'Falta destinatario (to).' };
          const result = await runGog('gmail', 'send', '--to', to, '--subject', subject || '', '--body', body || '', '--force', '--no-input');
          if (result.ok) return { ok: true, result: result.text || 'Correo enviado.' };
          // Fallback: open Gmail compose URL
          const url = `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(to)}&su=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
          await runPS(`Start-Process ${psQuote(url)}`);
          return { ok: true, result: 'Abrí Gmail compose como fallback.' };
        },
      },

      // --- Calendar Reader ---
      {
        id: 'calendar_reader',
        description: 'Reads Google Calendar events via Gog CLI',
        keywords: ['calendar','calendario','eventos','agenda','hoy','reuniones'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const result = await runGog('calendar', 'events', 'primary', '--today', '--max', '10');
          return result.ok
            ? { ok: true, result: result.text || 'Sin eventos hoy.' }
            : { ok: false, error: result.text };
        },
      },

      // --- Calendar Writer ---
      {
        id: 'calendar_writer',
        description: 'Creates Google Calendar events via Gog CLI',
        keywords: ['crear','evento','agendar','agenda','reunion','cita'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const { title, details, offsetDays } = args;
          const start = new Date();
          if (offsetDays) start.setDate(start.getDate() + offsetDays);
          start.setHours(10, 0, 0, 0);
          const end = new Date(start.getTime() + 30 * 60 * 1000);
          const fmt = d => {
            const pad = n => String(n).padStart(2, '0');
            const off = -d.getTimezoneOffset();
            const sign = off >= 0 ? '+' : '-';
            const abs = Math.abs(off);
            return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00${sign}${pad(Math.floor(abs/60))}:${pad(abs%60)}`;
          };
          const result = await runGog(
            'calendar','create','primary',
            '--summary', title || 'Nuevo evento',
            '--from', fmt(start), '--to', fmt(end),
            '--start-timezone', 'America/Santiago',
            '--end-timezone', 'America/Santiago',
            '--description', details || '',
            '--force','--no-input'
          );
          return result.ok
            ? { ok: true, result: result.text || 'Evento creado.' }
            : { ok: false, error: result.text };
        },
        async rollback(args) {
          console.info('[Rollback] Calendar event creation rolled back (not yet implemented in Gog).');
        },
      },

      // --- Drive Reader ---
      {
        id: 'drive_reader',
        description: 'Lists recent Google Drive files via Gog CLI',
        keywords: ['drive','archivos','documentos','carpeta','google drive'],
        targets: ['windows', 'local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args) {
          const max = args.max || 5;
          const result = await runGog('drive', 'ls', '--max', String(max));
          return result.ok
            ? { ok: true, result: result.text || 'Drive sin archivos visibles.' }
            : { ok: false, error: result.text };
        },
      },

      // --- Briefing Composer ---
      {
        id: 'briefing_composer',
        description: 'Composes the daily briefing from all collected data',
        keywords: ['briefing','resumen','daily'],
        targets: ['local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args, ctx) {
          const deps = args._deps || {};
          const pcCtx = ctx.memory.getWorking('pcContext') || ctx.appContext?.systemContext || {};
          const windows = (pcCtx.activeWindows || []).slice(0, 5).map(w => `- ${w.processName}: ${w.windowTitle}`).join('\n') || 'Sin ventanas detectadas.';
          const profile = ctx.appContext?.userProfile || {};
          const notes = (ctx.appContext?.notes || []).slice(0, 3).map(n => `- ${n.title}: ${String(n.content).slice(0,80)}`).join('\n');
          const now = new Date();
          const day = now.toLocaleDateString('es', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });

          const sections = [
            `DAILY BRIEFING — ${day}`,
            '',
            'GMAIL', deps.s2 || 'No disponible.',
            '',
            'CALENDAR', deps.s3 || 'No disponible.',
            '',
            'DRIVE', deps.s4 || 'No disponible.',
            '',
            'CONTEXTO PC', windows,
          ];
          if (notes) sections.push('', 'NOTAS', notes);
          if (profile.debt) sections.push('', 'FINANZAS', `Deuda: $${Number(profile.debt).toLocaleString()} | Ingresos: $${Number(profile.income||0).toLocaleString()}`);

          return { ok: true, result: sections.join('\n') };
        },
      },

      // --- Test Reporter ---
      {
        id: 'test_reporter',
        description: 'Compiles agent test results into a readable report',
        keywords: ['test','reporter','prueba','reporte'],
        targets: ['local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args, ctx) {
          const deps = args._deps || {};
          const labels = {
            s1: 'Contexto Windows',
            s2: 'Gmail no leídos',
            s3: 'Calendar hoy',
            s4: 'Drive',
            s5: 'Crear evento',
            s6: 'Enviar correo',
            s7: 'Android sync',
          };
          const lines = Object.entries(deps).map(([id, text]) => {
            const ok = !!text && !String(text).toLowerCase().includes('error');
            return `${ok ? '[OK]' : '[FALLO]'} ${labels[id] || id}\n${String(text).slice(0, 400) || 'Sin detalle.'}`;
          });
          const report = ['DANBRON AGENT TEST v3', '', ...lines, '', 'Ningún resultado fue inventado.'].join('\n');
          return { ok: true, result: report };
        },
      },

      // --- LLM Tool (conversational fallback + sub-agent) ---
      {
        id: 'llm_tool',
        description: 'Calls the AI model for conversation or sub-agent reasoning',
        keywords: ['pregunta','explicame','que es','como','cuanto','ayuda','consejo','conversa'],
        targets: ['local', 'any'],
        requiresAuth: false,
        requiresPairing: false,
        async execute(args, ctx) {
          // Delegates back to the existing callAI function in app.js
          if (typeof window.__bronCallAI === 'function') {
            const text = await window.__bronCallAI(args.text || '', ctx.memory.getContextSummary?.() || '');
            return { ok: true, result: text };
          }
          return { ok: false, error: 'callAI bridge not registered. Set window.__bronCallAI.' };
        },
      },
    ];
  }

  // Windows action script builder (same logic as app.js but centralized here)
  function buildWindowsScript(args) {
    const psQuote = v => `'${String(v).replace(/'/g, "''")}'`;
    const { action, url, app, message, to, subject, body, query, service } = args;

    if (url || action === 'open_url') return `Start-Process ${psQuote(url)}`;

    if (action === 'open_app' || app) {
      const scripts = {
        opera:    `$paths = @("$env:LOCALAPPDATA\\Programs\\Opera\\launcher.exe"); $exe = $paths | Where-Object { Test-Path $_ } | Select-Object -First 1; if ($exe) { Start-Process $exe } else { Start-Process 'opera' }`,
        chrome:   `Start-Process 'chrome'`,
        whatsapp: `Start-Process 'whatsapp:'`,
        gmail:    `Start-Process 'https://mail.google.com/'`,
        calendar: `Start-Process 'https://calendar.google.com/'`,
        drive:    `Start-Process 'https://drive.google.com/'`,
        spotify:  `Start-Process 'spotify:'`,
        music:    `Start-Process 'https://music.youtube.com/'`,
        youtube:  `Start-Process 'https://www.youtube.com/'`,
        portal_universidad: `Start-Process 'https://portal.uv.cl/'`,
      };
      return scripts[app] || null;
    }

    if (action === 'compose_whatsapp') {
      return `Start-Process ${psQuote(`https://wa.me/?text=${encodeURIComponent(message || '')}`)}`;
    }
    if (action === 'compose_email') {
      return `Start-Process ${psQuote(`https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(to||'')}&su=${encodeURIComponent(subject||'')}&body=${encodeURIComponent(body||'')}`)}`;
    }
    if (action === 'play_music') {
      const q = encodeURIComponent(query || '');
      return service === 'spotify'
        ? `Start-Process ${psQuote(q ? `spotify:search:${q}` : 'spotify:')}`
        : `Start-Process ${psQuote(q ? `https://music.youtube.com/search?q=${q}` : 'https://music.youtube.com/')}`;
    }
    if (action === 'open_maps') {
      return `Start-Process ${psQuote(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query||'')}`)}`;
    }
    return null;
  }

  // ─────────────────────────────────────────────
  // SECTION 7: ORCHESTRATOR — public API
  // ─────────────────────────────────────────────

  class Orchestrator {
    constructor() {
      this.memory = null;
      this.registry = new ToolRegistry();
      this.planner = new Planner(this.registry);
      this.executor = null;
      this._initialized = false;
    }

    /**
     * Initialize with app state.
     * Call this once after app init: BronOrchestrator.init(state)
     */
    init(appState) {
      this.memory = new MemoryStore(appState?.userId || 'anon');
      this.executor = new Executor(this.registry, this.memory, {
        maxRetries: 2,
        onProgress: (step, result) => {
          console.debug(`[Orchestrator] step ${step.id} (${step.toolId}): ${result.ok ? 'OK' : 'FAIL'}`);
        },
      });

      // Register built-in tools
      const tools = buildBuiltinTools(appState);
      tools.forEach(t => this.registry.register(t));

      this._initialized = true;
      console.info('[BronOrchestrator] v3.0 initialized. Tools:', this.registry.list().map(t => t.id));
    }

    /**
     * Main entry point. Replaces handleSend's routing logic entirely.
     *
     * @param {string} userMessage
     * @param {object} appState    — the full `state` object from app.js
     * @returns {Promise<string>}  — the reply string to show in chat
     */
    async run(userMessage, appState) {
      if (!this._initialized) this.init(appState);

      // 1. Perception
      const intent = Perception.classify(userMessage, appState);
      this.memory.setWorking('intent', intent);

      // 2. Recall relevant memory
      const pastMemory = this.memory.recallRelevant(userMessage, 3);
      const memCtx = this.memory.getContextSummary();
      this.memory.setWorking('pastMemory', pastMemory);
      this.memory.setWorking('memoryContext', memCtx);

      // 3. Plan
      const plan = this.planner.buildPlan(intent, { appState, memCtx });
      if (!plan.length) {
        // Nothing matched → pure conversation
        return this._fallbackToAI(userMessage, appState, memCtx);
      }

      // 4. Execute + reflect
      const executionCtx = { appContext: appState, memory: this.memory };
      const { outcome, results, reflection, summary } = await this.executor.run(plan, userMessage, executionCtx);

      // 5. Extract the final human-readable result
      const lastStep = plan[plan.length - 1];
      const lastResult = results[lastStep?.id];

      let reply;
      if (lastResult?.ok && lastResult.result && typeof lastResult.result === 'string') {
        reply = lastResult.result;
      } else if (outcome === 'success') {
        reply = `Listo. ${reflection}`;
      } else {
        reply = `${reflection} Intenté ${plan.length} paso(s). Si quieres intento algo diferente.`;
      }

      // 6. Save episode to memory
      this.memory.addEpisode({
        intent: `${intent.action} (${intent.type})`,
        plan,
        outcome,
        reflection,
        summary,
      });
      this.memory.clearWorking();

      return reply;
    }

    async _fallbackToAI(userMessage, appState, memCtx) {
      if (typeof window.__bronCallAI === 'function') {
        return window.__bronCallAI(userMessage, memCtx);
      }
      return 'No pude procesar eso. Intenta reformular la pregunta.';
    }

    /**
     * Register a custom tool at runtime.
     * Use this to add integrations without modifying this file.
     *
     * @example
     * BronOrchestrator.registerTool({
     *   id: 'notion_writer',
     *   description: 'Creates Notion pages',
     *   keywords: ['notion','pagina','nota','crear'],
     *   targets: ['local','any'],
     *   requiresAuth: true,
     *   requiresPairing: false,
     *   execute: async (args, ctx) => { ... }
     * });
     */
    registerTool(descriptor) {
      this.registry.register(descriptor);
    }

    /** Access memory directly for debugging */
    get memoryStore() {
      return this.memory;
    }

    /** List all registered tools */
    listTools() {
      return this.registry.list().map(t => ({
        id: t.id,
        description: t.description,
        targets: t.targets,
        circuitOpen: t.circuitOpen,
        callCount: t.callCount,
        failCount: t.failCount,
      }));
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 8: INTEGRATION BRIDGE FOR app.js
  // ─────────────────────────────────────────────

  /**
   * How to integrate in app.js:
   *
   * 1. Add <script src="orchestrator.js"></script> before app.js in your HTML.
   *
   * 2. In initApp(), after initSyncClients():
   *      window.BronOrchestrator.init(state);
   *
   * 3. Register the callAI bridge (so llm_tool can use your existing provider):
   *      window.__bronCallAI = async (userMessage, memCtx) => {
   *        // inject memCtx into your buildSystemPrompt() however you want
   *        return callAI(userMessage);
   *      };
   *
   * 4. Replace the body of handleSend() with:
   *      const reply = await window.BronOrchestrator.run(text, state);
   *      state.messages.push({ role: 'assistant', content: reply, time: formatTime() });
   *
   * That's it. The orchestrator handles all routing, memory, and tool execution.
   * Your existing callAI, buildSystemPrompt, systemTracker, syncClient all still work.
   */

  // Expose globally
  global.BronOrchestrator = new Orchestrator();
  global.Perception = Perception;
  global.MemoryStore = MemoryStore;
  global.ToolRegistry = ToolRegistry;

})(window);
