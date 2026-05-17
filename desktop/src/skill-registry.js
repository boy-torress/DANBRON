/**
 * Danbron Skill Registry
 * Lightweight adapter for OpenClaw-style skills.
 * Skills are trusted by the user and may delegate to external runtimes
 * such as gog, maton, openclaw, or agent-browser when those commands are installed.
 */

const DanbronSkillRegistry = (() => {
  const trustedMode = true;
  const skills = [
    {
      id: 'ai-daily-briefing',
      name: 'AI Daily Briefing',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Daily focus briefing from notes, context, tasks, calendar, and memory.',
      triggers: [
        'briefing', 'daily briefing', 'morning briefing', 'resumen del dia',
        'que tengo hoy', 'que necesito saber', 'start my day', 'what is on my plate'
      ],
      actions: ['daily_briefing']
    },
    {
      id: 'gmail',
      name: 'Gmail',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['maton', 'gog'],
      description: 'Gmail read/search/draft/send integration. Danbron currently uses safe draft/open fallbacks.',
      triggers: ['gmail', 'correo', 'email', 'mail', 'inbox', 'bandeja'],
      actions: ['compose_email', 'open_app']
    },
    {
      id: 'gog',
      name: 'Gog',
      source: 'openclaw-compatible',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['gog'],
      description: 'Google Workspace: Gmail, Calendar, Drive, Docs, Sheets, Contacts, Maps.',
      triggers: [
        'gmail', 'correo', 'email', 'mail', 'calendar', 'calendario', 'agenda',
        'drive', 'docs', 'documento', 'sheets', 'hoja', 'contactos', 'maps', 'mapa'
      ],
      actions: [
        'compose_email',
        'create_calendar_event',
        'open_workspace_app',
        'search_workspace'
      ]
    },
    {
      id: 'soul',
      name: 'Soul',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['openclaw-soul-plugin'],
      description: 'Proactive memory and reflection layer for Danbron.',
      triggers: ['soul', 'memoria', 'recuerdas', 'proactivo', 'pensar solo'],
      actions: ['memory_reflect', 'proactive_followup']
    },
    {
      id: 'openclaw-usecase-catalog',
      name: 'OpenClaw Use Case Catalog',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Reference skill for use cases, ideas, and roadmap inspiration.',
      triggers: ['casos de uso', 'ideas', 'inspiracion', 'que puede hacer openclaw', 'roadmap'],
      actions: ['suggest_usecases']
    },
    {
      id: 'clawpilot',
      name: 'Clawpilot',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['openclaw'],
      description: 'OpenClaw expert/security/deployment reference for Danbron Gateway.',
      triggers: ['clawpilot', 'openclaw', 'gateway', 'seguridad', 'deploy', 'canales'],
      actions: ['audit_gateway', 'recommend_setup']
    },
    {
      id: 'api-gateway',
      name: 'API Gateway',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['maton'],
      description: 'Third-party API connection pattern with approval-gated writes.',
      triggers: ['api gateway', 'conectar api', 'integracion', 'oauth', 'maton'],
      actions: ['api_read', 'api_write_request']
    },
    {
      id: 'agent-browser-clawdbot',
      name: 'Agent Browser',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['agent-browser'],
      description: 'Headless browser automation with accessibility snapshots and ref-based element selection.',
      triggers: [
        'agent browser', 'agent-browser', 'browser agent', 'navegador agente',
        'automatiza esta pagina', 'automatizar pagina', 'rellena formulario',
        'haz click', 'saca snapshot', 'snapshot', 'browser session'
      ],
      actions: ['browser_open', 'browser_snapshot', 'browser_click', 'browser_fill', 'browser_wait']
    },
    {
      id: 'setup',
      name: 'Setup',
      source: 'clawhub-adapted',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['openclaw'],
      description: 'Setup and hardening checklist for assistant gateways.',
      triggers: ['setup', 'configurar', 'hardening', 'allowlist', 'permisos'],
      actions: ['setup_checklist']
    },
    {
      id: 'weather',
      name: 'Weather',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Check current weather and forecast via web search or API.',
      triggers: ['clima', 'tiempo', 'weather', 'lluvia', 'temperatura', 'hace frio', 'hace calor', 'pronostico'],
      actions: ['check_weather', 'web_search']
    },
    {
      id: 'timer-reminders',
      name: 'Timer & Reminders',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Set timers, alarms and reminders. Uses system notifications.',
      triggers: ['timer', 'temporizador', 'alarma', 'recordatorio', 'recuerdame', 'remind', 'en 5 minutos', 'en 10 minutos', 'en una hora'],
      actions: ['set_timer', 'set_reminder', 'list_reminders']
    },
    {
      id: 'web-search',
      name: 'Web Search',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Search the web for information, news, prices, and answers.',
      triggers: ['busca', 'buscar', 'google', 'search', 'investiga', 'precio de', 'cuanto cuesta', 'noticias', 'news', 'que es'],
      actions: ['web_search', 'open_url']
    },
    {
      id: 'file-manager',
      name: 'File Manager',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Find, open, organize and manage files and folders on the PC.',
      triggers: ['archivo', 'carpeta', 'file', 'folder', 'busca archivo', 'abrir archivo', 'descargas', 'downloads', 'documentos'],
      actions: ['find_file', 'open_file', 'list_directory']
    },
    {
      id: 'music-media',
      name: 'Music & Media Control',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Control music playback, open Spotify, YouTube Music, etc.',
      triggers: ['musica', 'music', 'spotify', 'youtube music', 'play', 'pausa', 'siguiente cancion', 'volumen'],
      actions: ['open_app', 'media_control']
    },
    {
      id: 'calculator',
      name: 'Calculator',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Perform calculations, currency conversions, and math operations.',
      triggers: ['calcula', 'cuanto es', 'calculadora', 'convertir', 'conversion', 'dolares', 'pesos', 'porcentaje'],
      actions: ['calculate', 'convert_currency']
    },
    {
      id: 'translator',
      name: 'Translator',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Translate text between languages using AI.',
      triggers: ['traduce', 'traducir', 'translate', 'en ingles', 'en espanol', 'como se dice'],
      actions: ['translate_text']
    },
    {
      id: 'system-cleanup',
      name: 'System Cleanup',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Clean temp files, free disk space, optimize PC performance.',
      triggers: ['limpiar', 'cleanup', 'lento', 'optimizar', 'liberar espacio', 'temp files', 'cache', 'rendimiento'],
      actions: ['cleanup_temp', 'disk_analysis', 'optimize']
    },
    {
      id: 'screenshot-ocr',
      name: 'Screenshot & OCR',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: false,
      description: 'Take screenshots, read screen text via OCR, analyze what is on screen.',
      triggers: ['screenshot', 'captura', 'que hay en pantalla', 'lee la pantalla', 'ocr', 'que dice ahi', 'que ves'],
      actions: ['take_screenshot', 'ocr_read', 'screen_analysis']
    },
    {
      id: 'social-media',
      name: 'Social Media',
      source: 'builtin',
      trustLevel: 'trusted',
      allowExternalRuntime: true,
      runtimes: ['agent-browser'],
      description: 'Open and interact with social media: Instagram, TikTok, Twitter, Facebook.',
      triggers: ['instagram', 'tiktok', 'twitter', 'facebook', 'redes sociales', 'publicar', 'post', 'story'],
      actions: ['open_app', 'browser_open']
    }
  ];

  function normalize(text) {
    return String(text || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  function findSkillForText(text) {
    const normalized = normalize(text);
    return skills.find(skill => skill.triggers.some(trigger => normalized.includes(trigger))) || null;
  }

  function listSkills() {
    return skills.map(skill => ({
      id: skill.id,
      name: skill.name,
      source: skill.source,
      description: skill.description,
      actions: skill.actions,
      trustLevel: skill.trustLevel,
      allowExternalRuntime: !!skill.allowExternalRuntime
    }));
  }

  return {
    findSkillForText,
    listSkills,
    trustedMode
  };
})();
