/**
 * Danbron Desktop — Native Tauri App Logic with Full Sync
 * Chat with Bron from your Windows PC.
 * Uses native WebView2 — no Chromium bundle needed.
 * Now synced with Android phone via backend.
 */

// ── Sync Clients ──
let syncClient;
let systemTracker;

// ── State ──
const state = {
    apiKey: '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',  // Danbron Groq API key
    email: '',
    userName: '',
    userId: '',
    deviceId: '',
    pairedDeviceId: '',
    pairedDeviceName: '',
    userProfile: null,
    messages: [],
    isTyping: false,
    isPinned: false,
    isAuthenticated: false,
    isPaired: false,
    recentEvents: [],
    systemContext: null,
};

// ── DOM ──
const $ = (id) => document.getElementById(id);

// ── Init ──
document.addEventListener('DOMContentLoaded', async () => {
    // Initialize sync clients
    await initSyncClients();
    
    loadState();
    
    // Check if already authenticated
    if (state.isAuthenticated) {
        await checkPairedDevice();
        showChat();
    } else {
        showSetup();
    }
    
    setupEvents();
});

async function initSyncClients() {
    // Import sync client from sync-client.js
    if (typeof DanbronSyncClient !== 'undefined') {
        syncClient = new DanbronSyncClient();
        
        // Set up event listeners
        syncClient.on('authenticated', (data) => {
            state.isAuthenticated = true;
            state.email = data.user.email;
            state.userName = data.user.name;
            state.userId = data.user.id;
            saveState();
        });

        syncClient.on('device_paired', async (data) => {
            state.isPaired = true;
            state.pairedDeviceId = data.pairedDeviceId;
            await checkPairedDevice();
            saveState();
            showNotification('¡Emparejado! Ahora recibo tus datos desde el teléfono.');
        });

        syncClient.on('data_received', (data) => {
            if (data.data) {
                state.userProfile = data.data;
                saveState();
            }
        });

        syncClient.on('event_recorded', (data) => {
            console.log('Event recorded:', data.eventType);
        });

        // Initialize system tracker
        if (typeof WindowsSystemTracker !== 'undefined') {
            systemTracker = new WindowsSystemTracker(syncClient);
        }
    }
}

async function checkPairedDevice() {
    if (!syncClient || !state.isAuthenticated) return;
    
    try {
        const paired = await syncClient.checkPairedDevice();
        if (paired) {
            state.isPaired = true;
            state.pairedDeviceId = paired.id;
            state.pairedDeviceName = paired.deviceName;
            
            // Start system monitoring
            if (systemTracker && !systemTracker.isMonitoring) {
                await systemTracker.startMonitoring();
            }
            
            // Get synced data
            const data = await syncClient.getSyncedData();
            if (data) {
                state.userProfile = data;
            }
        }
    } catch (error) {
        console.error('Failed to check paired device:', error);
    }
}

function setupEvents() {
    $('startBtn').addEventListener('click', () => handleAuth());
    $('skipSyncBtn').addEventListener('click', () => handleSkipAuth());
    $('pairingBtn').addEventListener('click', () => showPairingDialog());
    $('sendBtn').addEventListener('click', handleSend);
    $('chatInput').addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) handleSend(); });
    $('clearChatBtn').addEventListener('click', handleClearChat);
    $('settingsBtn').addEventListener('click', () => openSettings());
    $('cancelSettingsBtn').addEventListener('click', () => closeSettings());
    $('saveSettingsBtn').addEventListener('click', () => saveSettings());
    $('pinBtn').addEventListener('click', togglePin);
    
    // New screen buttons
    const profileBtn = $('profileBtn');
    const progressBtn = $('progressBtn');
    const notesBtn = $('notesBtn');
    if (profileBtn) profileBtn.addEventListener('click', () => showProfile());
    if (progressBtn) progressBtn.addEventListener('click', () => showProgress());
    if (notesBtn) notesBtn.addEventListener('click', () => showNotes());
    
    const closeProfileBtn = $('closeProfileBtn');
    const closeProgressBtn = $('closeProgressBtn');
    const closeNotesBtn = $('closeNotesBtn');
    if (closeProfileBtn) closeProfileBtn.addEventListener('click', showChat);
    if (closeProgressBtn) closeProgressBtn.addEventListener('click', showChat);
    if (closeNotesBtn) closeNotesBtn.addEventListener('click', showChat);

    // Titlebar drag
    $('titlebar').addEventListener('mousedown', (e) => {
        if (e.target.closest('.titlebar-controls')) return;
        if (window.__TAURI__) {
            window.__TAURI__.window.getCurrentWindow().startDragging();
        }
    });

    // Window controls via Tauri API
    $('minimizeBtn').addEventListener('click', async () => {
        if (window.__TAURI__) {
            await window.__TAURI__.window.getCurrentWindow().minimize();
        }
    });

    $('closeBtn').addEventListener('click', async () => {
        if (window.__TAURI__) {
            await window.__TAURI__.window.getCurrentWindow().hide();
        }
    });

    // Close settings modal on overlay click
    $('settingsModal').addEventListener('click', (e) => {
        if (e.target === $('settingsModal')) closeSettings();
    });

    // Mascot animations
    animateMascots();
}

// ── Screens ──
function showSetup() {
    $('setupScreen').classList.add('active');
    $('chatScreen').classList.remove('active');
    $('syncCodeInput').value = '';
}

function showChat() {
    $('setupScreen').classList.remove('active');
    $('chatScreen').classList.add('active');
    $('profileScreen').classList.remove('active');
    $('progressScreen').classList.remove('active');
    $('notesScreen').classList.remove('active');

    if (state.messages.length === 0) {
        const hour = new Date().getHours();
        const greeting = hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches';
        const namePart = state.userName ? ` ${state.userName}` : '';
        state.messages.push({
            role: 'assistant',
            content: `¡${greeting}${namePart}! 👋 Soy Bron, tu asistente en el computador. Estoy aquí para ayudarte mientras trabajas, estudias o simplemente necesites un consejo. ¿Qué necesitas hoy?`,
            time: formatTime()
        });
        saveState();
    }

    renderMessages();
    $('chatInput').focus();
}

function showProfile() {
    $('chatScreen').classList.remove('active');
    $('profileScreen').classList.add('active');
    renderProfile();
}

function showProgress() {
    $('chatScreen').classList.remove('active');
    $('progressScreen').classList.add('active');
    renderProgress();
}

function showNotes() {
    $('chatScreen').classList.remove('active');
    $('notesScreen').classList.add('active');
    renderNotes();
}

function renderProfile() {
    const u = state.userProfile || {};
    const html = `
        <div class="profile-field">
            <label>Nombre</label>
            <input type="text" value="${u.name || state.userName || ''}" placeholder="Tu nombre">
        </div>
        <div class="profile-field">
            <label>Email</label>
            <input type="email" value="${state.email || ''}" disabled>
        </div>
        <div class="profile-field">
            <label>Ingresos Mensuales ($)</label>
            <input type="number" value="${u.income || ''}" placeholder="0">
        </div>
        <div class="profile-field">
            <label>Gastos Mensuales ($)</label>
            <input type="number" value="${u.expenses || ''}" placeholder="0">
        </div>
        <div class="profile-field">
            <label>Deuda Total ($)</label>
            <input type="number" value="${u.debt || ''}" placeholder="0">
        </div>
        <div class="screen-actions">
            <button class="btn-ghost" onclick="showChat()">Cancelar</button>
            <button class="btn-gold" onclick="saveProfile()">Guardar Perfil</button>
        </div>
    `;
    $('profileContent').innerHTML = html;
}

function renderProgress() {
    const u = state.userProfile || {};
    const debt = u.debt || 0;
    const income = u.income || 0;
    const monthly = income * 0.6;
    const remaining = debt - monthly;
    const pct = debt > 0 ? Math.min(100, (monthly / debt) * 100) : 100;
    
    const html = `
        <div class="progress-card">
            <h3>💰 Estado de Deuda</h3>
            <p style="color: var(--text-secondary); font-size: 24px; font-weight: 700; color: var(--red);">$${debt.toLocaleString()}</p>
            <p style="color: var(--text-tertiary); font-size: 12px; margin-top: 8px;">Pagando: $${monthly.toLocaleString()}/mes</p>
            <div class="progress-bar" style="margin-top: 12px;">
                <div class="progress-fill" style="width: ${pct}%"></div>
            </div>
        </div>
        <div class="progress-card">
            <h3>🎯 Hábitos Activos</h3>
            <p style="color: var(--text-secondary); font-size: 13px;">5 hábitos siendo monitoreados</p>
            <p style="color: var(--text-tertiary); font-size: 12px; margin-top: 8px;">Racha actual: En progreso 🔥</p>
        </div>
    `;
    $('progressContent').innerHTML = html;
}

function renderNotes() {
    const notes = state.notes || [];
    if (notes.length === 0) {
        $('notesContent').innerHTML = `
            <div style="text-align: center; padding: 40px 20px; color: var(--text-tertiary);">
                <p>📝 No hay notas aún</p>
                <p style="font-size: 12px; margin-top: 8px;">Tus notas sincronizadas aparecerán aquí</p>
            </div>
        `;
        return;
    }
    
    let html = '<div class="notes-grid">';
    notes.forEach((note, idx) => {
        html += `
            <div class="note-item">
                <h4>${note.title || 'Sin título'}</h4>
                <p>${note.content || ''}</p>
                <button class="note-delete" onclick="deleteDesktopNote(${idx})">✕</button>
            </div>
        `;
    });
    html += '</div>';
    $('notesContent').innerHTML = html;
}

function saveProfile() {
    const inputs = $('profileContent').querySelectorAll('input');
    const updated = {
        ...state.userProfile,
        name: inputs[0].value,
        income: parseFloat(inputs[2].value) || 0,
        expenses: parseFloat(inputs[3].value) || 0,
        debt: parseFloat(inputs[4].value) || 0
    };
    state.userProfile = updated;
    saveState();
    showNotification('Perfil guardado ✓');
    showChat();
}

function deleteDesktopNote(idx) {
    state.notes.splice(idx, 1);
    saveState();
    renderNotes();
    showNotification('Nota eliminada');
}

// ── Handlers ──
async function handleAuth() {
    const email = $('emailInput')?.value?.trim();
    
    if (!email) {
        showNotification('Por favor ingresa tu email');
        return;
    }

    if (!syncClient) {
        showNotification('Sistema de sync no disponible');
        return;
    }

    $('startBtn').textContent = "Autenticando...";
    $('startBtn').disabled = true;

    try {
        await syncClient.authenticate(email);
        await syncClient.registerDevice('Danbron Desktop');
        
        state.isAuthenticated = true;
        state.email = email;
        state.deviceId = syncClient.deviceId;
        
        saveState();
        showPairingDialog();
    } catch (error) {
        showNotification('Error: ' + error.message);
    } finally {
        $('startBtn').textContent = "Conectar →";
        $('startBtn').disabled = false;
    }
}

function handleSkipAuth() {
    state.userId = 'local_' + Math.random().toString(36).substring(2, 9);
    state.userName = 'Amigo';
    state.userProfile = null;
    state.messages = [];
    state.isAuthenticated = false;
    saveState();
    showChat();
}

async function showPairingDialog() {
    // Remove existing modal if present
    const $existing = document.getElementById('pairingModal');
    if ($existing) $existing.remove();
    
    const $pairingModal = document.createElement('div');
    $pairingModal.className = 'modal-overlay active';
    $pairingModal.id = 'pairingModal';
    $pairingModal.innerHTML = `
        <div class="modal-content">
            <h2>Emparejar Dispositivo</h2>
            <div class="pairing-options">
                <div class="pairing-option">
                    <h3>📱 Desde tu Teléfono</h3>
                    <p>Abre Danbron en tu Android y busca "Emparejar"</p>
                    <input type="text" id="pairingCodeInput" placeholder="Ingresa el código de 6 dígitos" maxlength="6">
                    <button onclick="confirmPairingCode()">Confirmar</button>
                </div>
                <div class="pairing-divider">O</div>
                <div class="pairing-option">
                    <h3>💻 Este PC</h3>
                    <p>Dale este código a tu teléfono:</p>
                    <div class="pairing-code-display" id="pcPairingCode">Generando...</div>
                    <button onclick="copyPairingCode()">Copiar Código</button>
                </div>
            </div>
            <button class="btn-secondary" onclick="skipPairing()">Continuar sin emparejar</button>
        </div>
    `;
    
    document.body.appendChild($pairingModal);

    // Generate pairing code for this device
    try {
        const code = await syncClient.getPairingCode();
        $('pcPairingCode').textContent = code;
    } catch (error) {
        $('pcPairingCode').textContent = 'Error';
    }
}

async function confirmPairingCode() {
    const code = $('pairingCodeInput')?.value?.trim();
    
    if (!code || !/^\d{6}$/.test(code)) {
        showNotification('Código debe ser exactamente 6 dígitos (0-9)');
        return;
    }

    try {
        await syncClient.pairDevice(code, syncClient.deviceId);
        
        const $modal = $('pairingModal');
        if ($modal) $modal.remove();
        
        state.isPaired = true;
        saveState();
        
        // Start system monitoring after pairing
        if (systemTracker) {
            await systemTracker.startMonitoring();
        }
        
        showChat();
        showNotification('¡Emparejado exitosamente!');
    } catch (error) {
        showNotification('Error al emparejar: ' + error.message);
    }
}

function copyPairingCode() {
    const code = $('pcPairingCode').textContent;
    if (code && code !== 'Generando...' && code !== 'Error') {
        navigator.clipboard.writeText(code);
        showNotification('Código copiado!');
    }
}

function skipPairing() {
    const $modal = $('pairingModal');
    if ($modal) $modal.remove();
    showChat();
}

function logout() {
    state.isAuthenticated = false;
    state.email = '';
    state.userId = '';
    state.deviceId = '';
    state.pairedDeviceId = '';
    state.userName = '';
    state.userProfile = null;
    state.messages = [];
    state.isPaired = false;
    
    if (systemTracker) {
        systemTracker.stopMonitoring();
    }
    
    saveState();
    showSetup();
}

async function handleSend() {
    const text = $('chatInput').value.trim();
    if (!text || state.isTyping) return;

    state.messages.push({ role: 'user', content: text, time: formatTime() });
    $('chatInput').value = '';
    renderMessages();
    scrollToBottom();

    state.isTyping = true;
    showTyping();

    try {
        const reply = await callAI(text);
        state.messages.push({ role: 'assistant', content: reply, time: formatTime() });
    } catch (err) {
        // Show error as notification instead of in message history
        showNotification('Error: ' + err.message);
        // Remove the user message since we failed to respond
        state.messages.pop();
    }

    state.isTyping = false;
    hideTyping();
    saveState();
    renderMessages();
    scrollToBottom();
}

function handleClearChat() {
    state.messages = [];
    saveState();
    showChat();
}

async function togglePin() {
    state.isPinned = !state.isPinned;
    $('pinBtn').classList.toggle('pinned', state.isPinned);
    if (window.__TAURI__) {
        await window.__TAURI__.window.getCurrentWindow().setAlwaysOnTop(state.isPinned);
    }
}

function openSettings() {
    const syncStatus = state.isPaired 
        ? `Emparejado con ${state.pairedDeviceName}`
        : state.isAuthenticated
        ? 'Autenticado, sin emparejar'
        : 'No autenticado';
    
    const syncStatusColor = state.isPaired ? '#10B981' : state.isAuthenticated ? '#F59E0B' : '#EF4444';
    
    $('syncStatus').textContent = syncStatus;
    $('syncStatus').style.color = syncStatusColor;
    
    // Show pairing options if authenticated but not paired
    if (state.isAuthenticated && !state.isPaired) {
        $('pairingBtn').style.display = 'block';
    } else {
        $('pairingBtn').style.display = 'none';
    }
    
    // Update or create logout button
    let $logoutBtn = document.getElementById('logoutBtn');
    if (!$logoutBtn && state.isAuthenticated) {
        $logoutBtn = document.createElement('button');
        $logoutBtn.id = 'logoutBtn';
        $logoutBtn.className = 'btn-ghost';
        $logoutBtn.style.width = '100%';
        $logoutBtn.style.marginTop = '12px';
        $logoutBtn.style.color = '#FF5C5C';
        $logoutBtn.textContent = '🚪 Cerrar sesión';
        $logoutBtn.onclick = () => { closeSettings(); logout(); };
        const $modal = $('settingsModal');
        if ($modal) {
            $modal.appendChild($logoutBtn);
        }
    }
    
    if ($logoutBtn) {
        $logoutBtn.style.display = state.isAuthenticated ? 'block' : 'none';
    }
    
    $('settingsModal').classList.add('active');
}

function closeSettings() {
    $('settingsModal').classList.remove('active');
}

async function saveSettings() {
    // Settings saved automatically via state
    closeSettings();
}

function showNotification(message) {
    const $notif = document.createElement('div');
    $notif.className = 'notification';
    $notif.textContent = message;
    document.body.appendChild($notif);
    
    setTimeout(() => {
        $notif.style.opacity = '0';
        setTimeout(() => $notif.remove(), 300);
    }, 3000);
}

// ── AI API ──
async function callAI(userMessage) {
    let key = state.apiKey;  // Company Groq API key
    
    if (!key || key.trim() === '') {
        throw new Error('API key no configurada. Contacta al administrador.');
    }

    const systemPrompt = buildSystemPrompt();
    const history = state.messages.slice(-10);

    if (key.startsWith('gsk_')) {
        // Groq (OpenAI compatible)
        const messages = [
            { role: 'system', content: systemPrompt },
            ...history.map(m => ({ role: m.role, content: m.content })),
        ];

        const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${key}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                max_tokens: 600,
                temperature: 0.7,
                messages
            })
        });

        if (!resp.ok) { const t = await resp.text(); throw new Error(`Groq ${resp.status}`); }
        const data = await resp.json();
        return data.choices?.[0]?.message?.content || 'Sin respuesta';

    } else if (key.startsWith('sk-ant-')) {
        // Anthropic
        const messages = history.map(m => ({ role: m.role, content: m.content }));

        const resp = await fetch('https://api.anthropic.com/v1/messages', {
            method: 'POST',
            headers: {
                'x-api-key': key,
                'anthropic-version': '2023-06-01',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                model: 'claude-sonnet-4-20250514',
                max_tokens: 600,
                system: systemPrompt,
                messages
            })
        });

        if (!resp.ok) { const t = await resp.text(); throw new Error(`Anthropic ${resp.status}`); }
        const data = await resp.json();
        return data.content?.[0]?.text || 'Sin respuesta';

    } else {
        throw new Error('API key no reconocida.');
    }
}

function buildSystemPrompt() {
    const hour = new Date().getHours();
    const days = ['domingo','lunes','martes','miércoles','jueves','viernes','sábado'];
    const day = days[new Date().getDay()];

    let syncInfo = "";
    if (state.isPaired && state.userProfile) {
        const p = state.userProfile;
        const challenges = Array.isArray(p.challenges) ? p.challenges.join(', ') : 'Ninguno';
        syncInfo = `
[INFORMACIÓN CRÍTICA DEL TELÉFONO - ESTÁS SINCRONIZADO Y TIENES ACCESO TOTAL]:
- Nombre: ${p.name || 'Desconocido'}
- Ingresos: $${p.income || 0}
- Gastos: $${p.expenses || 0}
- Deuda actual: $${p.debt || 0}
- Trabajo: ${p.workStyle || 'Desconocido'} / ${p.employmentStatus || 'Desconocido'}
- Retos actuales: ${challenges}
- Dispositivo emparejado: ${state.pairedDeviceName || 'Tu teléfono'}

Eres exactamente la misma inteligencia artificial (Bron) de su aplicación móvil Danbron.
Si te pregunta por sus finanzas o su deuda, responde con los NÚMEROS EXACTOS de arriba. NO INVENTES NÚMEROS. NO DIGAS QUE NO TIENES ACCESO, PORQUE AHORA LO TIENES.`;
    }

    let systemContextInfo = "";
    if (systemTracker && systemTracker.trackedApps) {
        const recentApps = Array.from(systemTracker.trackedApps).slice(0, 5);
        if (recentApps.length > 0) {
            systemContextInfo = `

[CONTEXTO DEL SISTEMA WINDOWS]:
- Apps abiertas recientemente: ${recentApps.join(', ')}
- Hora: ${hour}:00
`;
        }
    }

    return `Eres Bron, el asistente personal de IA de Danbron (un zorrito dorado muy astuto y amigable). Estás en la versión DESKTOP (Windows nativo). Eres como el mejor amigo: directo, empático, sin rodeos, y das consejos MUY específicos y accionables.

USUARIO: ${state.userName}
HORA: ${hour}:00 del ${day}
PLATAFORMA: Windows Desktop
${syncInfo}
${systemContextInfo}

REGLAS:
1. Sé ESPECÍFICO: "cierra Instagram y trabaja 25 min con Pomodoro" no "sé productivo"
2. Responde siempre en español latinoamericano
3. Tono: zorrito amigo cercano que te da la verdad con cariño
4. Si parece procrastinar, díselo con amor
5. Máximo 4-5 oraciones por respuesta
6. NUNCA uses bullet points, escribe como conversación natural
7. Si pregunta de trabajo/estudio, da técnicas concretas
8. Si tienes acceso a su teléfono, menciona lo que viste relevante (ej: "Vi que tienes un correo de tu jefe", "Noté que jugaste ayer 4 horas")`;

// ── Render ──
function renderMessages() {
    const container = $('messagesContainer');
    container.innerHTML = '';
    state.messages.forEach(msg => {
        const div = document.createElement('div');
        div.className = `message ${msg.role}`;

        const content = document.createElement('span');
        content.textContent = msg.content;
        div.appendChild(content);

        if (msg.time) {
            const ts = document.createElement('span');
            ts.className = 'timestamp';
            ts.textContent = msg.time;
            div.appendChild(ts);
        }

        container.appendChild(div);
    });
}

function showTyping() {
    const div = document.createElement('div');
    div.className = 'typing-indicator';
    div.id = 'typingIndicator';
    div.innerHTML = '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';
    $('messagesContainer').appendChild(div);
    scrollToBottom();
}

function hideTyping() {
    const el = $('typingIndicator');
    if (el) el.remove();
}

function scrollToBottom() {
    const c = $('messagesContainer');
    c.scrollTop = c.scrollHeight;
}

function formatTime() {
    return new Date().toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
}

// ── Mascot Animation Engine ──
let globalTime = 0;

function animateMascots() {
    globalTime += 0.05;
    
    // Draw on setup screen if active
    if ($('setupScreen').classList.contains('active')) {
        drawFoxMascot('mascotCanvas', 160, globalTime, false);
    }
    
    // Draw on chat screen if active
    if ($('chatScreen').classList.contains('active')) {
        drawFoxMascot('chatMascot', 36, globalTime, state.isTyping);
    }
    
    requestAnimationFrame(animateMascots);
}

function drawFoxMascot(canvasId, size, time, isThinking) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const cx = size / 2, cy = size / 2;
    const s = size;
    
    ctx.clearRect(0, 0, size, size);

    // Breathing bounce
    const bounce = Math.sin(time) * (size * 0.02);
    
    // Blink logic
    const blinkCycle = time % 100;
    const isBlinking = blinkCycle > 95 && blinkCycle < 97;
    const blinkScale = isBlinking ? 0.1 : 1.0;
    
    // Ear twitch logic
    const earTwitchCycle = time % 120;
    const earTwitch = (earTwitchCycle > 110 && earTwitchCycle < 115) ? Math.sin(time * 5) * 0.1 : 0;

    ctx.save();
    ctx.translate(0, bounce);

    const foxColor = '#E8B84B';
    const foxDark = '#C97B2E';
    const eyeColor = '#0A0A0F';

    // Draw Ears
    drawEar(ctx, cx, cy, s, true, foxColor, foxDark, earTwitch);
    drawEar(ctx, cx, cy, s, false, foxColor, foxDark, -earTwitch);

    // Draw Head
    ctx.beginPath();
    const top = cy - s * 0.25;
    const bottom = cy + s * 0.35;
    const left = cx - s * 0.4;
    const right = cx + s * 0.4;
    
    ctx.moveTo(cx, bottom); // chin
    ctx.quadraticCurveTo(left, bottom - s * 0.1, left, cy); // left cheek
    ctx.quadraticCurveTo(left + s * 0.1, top, cx, top); // left top
    ctx.quadraticCurveTo(right - s * 0.1, top, right, cy); // right top
    ctx.quadraticCurveTo(right, bottom - s * 0.1, cx, bottom); // right cheek
    
    const headGrad = ctx.createLinearGradient(0, 0, s, s);
    headGrad.addColorStop(0, '#FFD97D');
    headGrad.addColorStop(0.5, foxColor);
    headGrad.addColorStop(1, foxDark);
    ctx.fillStyle = headGrad;
    ctx.fill();

    // White Muzzle
    ctx.beginPath();
    ctx.moveTo(cx, cy + s * 0.35); // chin
    ctx.quadraticCurveTo(cx - s * 0.25, cy + s * 0.1, cx, cy - s * 0.05); // left curve
    ctx.quadraticCurveTo(cx + s * 0.25, cy + s * 0.1, cx, cy + s * 0.35); // right curve
    ctx.fillStyle = '#FFF6E5';
    ctx.fill();

    // Nose
    ctx.beginPath();
    ctx.arc(cx, cy + s * 0.15, s * 0.05, 0, Math.PI * 2);
    ctx.fillStyle = eyeColor;
    ctx.fill();

    // Eyes
    const eyeRadius = s * 0.06;
    const eyeY = cy - s * 0.05;
    const leftEyeX = cx - s * 0.18;
    const rightEyeX = cx + s * 0.18;

    ctx.fillStyle = eyeColor;
    ctx.strokeStyle = eyeColor;
    ctx.lineWidth = s * 0.03;

    if (isThinking) {
        // Thinking eyes
        ctx.beginPath();
        ctx.arc(leftEyeX, eyeY, eyeRadius * 0.8, 0, Math.PI * 2);
        ctx.fill();
        
        ctx.beginPath();
        ctx.moveTo(rightEyeX - eyeRadius, eyeY);
        ctx.lineTo(rightEyeX + eyeRadius, eyeY);
        ctx.stroke();
    } else {
        // Normal eyes with blink
        ctx.beginPath();
        ctx.ellipse(leftEyeX, eyeY, eyeRadius, eyeRadius * blinkScale, 0, 0, Math.PI * 2);
        ctx.fill();
        
        ctx.beginPath();
        ctx.ellipse(rightEyeX, eyeY, eyeRadius, eyeRadius * blinkScale, 0, 0, Math.PI * 2);
        ctx.fill();
    }

    ctx.restore();
}

function drawEar(ctx, cx, cy, size, isLeft, color, innerColor, twitch) {
    const dir = isLeft ? -1 : 1;
    ctx.save();
    ctx.translate(cx + size * 0.25 * dir, cy - size * 0.2);
    ctx.rotate(twitch);
    
    // Outer Ear
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.quadraticCurveTo(size * 0.1 * dir, -size * 0.3, size * 0.2 * dir, -size * 0.35);
    ctx.quadraticCurveTo(size * 0.05 * dir, -size * 0.2, -size * 0.15 * dir, -size * 0.05);
    ctx.fillStyle = color;
    ctx.fill();
    
    // Inner Ear
    ctx.beginPath();
    ctx.moveTo(0, -size * 0.05);
    ctx.quadraticCurveTo(size * 0.05 * dir, -size * 0.2, size * 0.15 * dir, -size * 0.25);
    ctx.quadraticCurveTo(size * 0.02 * dir, -size * 0.15, -size * 0.05 * dir, -size * 0.05);
    ctx.fillStyle = innerColor;
    ctx.fill();
    
    ctx.restore();
}

// ── Persistence ──
function saveState() {
    localStorage.setItem('danbron_desktop', JSON.stringify({
        email: state.email,
        userName: state.userName,
        userId: state.userId,
        deviceId: state.deviceId,
        pairedDeviceId: state.pairedDeviceId,
        pairedDeviceName: state.pairedDeviceName,
        userProfile: state.userProfile,
        messages: state.messages.slice(-50),
        isAuthenticated: state.isAuthenticated,
        isPaired: state.isPaired,
    }));
}

function loadState() {
    try {
        const raw = localStorage.getItem('danbron_desktop');
        if (raw) {
            const d = JSON.parse(raw);
            state.email = d.email || '';
            state.userName = d.userName || '';
            state.userId = d.userId || '';
            state.deviceId = d.deviceId || '';
            state.pairedDeviceId = d.pairedDeviceId || '';
            state.pairedDeviceName = d.pairedDeviceName || '';
            state.userProfile = d.userProfile || null;
            state.messages = d.messages || [];
            state.isAuthenticated = d.isAuthenticated || false;
            state.isPaired = d.isPaired || false;
        }
    } catch (_) {}
}
