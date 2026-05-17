if (typeof window !== 'undefined' && window.__DANBRON_APP_LOADED__) {
    console.warn('Danbron app already loaded, skipping duplicate script execution.');
} else {
/**
 * Danbron Desktop - Native Tauri App Logic with Full Sync
 * Chat with Bron from your Windows PC.
 * Uses native WebView2 - no Chromium bundle needed.
 * Now synced with Android phone via backend.
 */

// Sync Clients
let syncClient;
let systemTracker;
let commandPollTimer;
let sharedSyncTimer;
let sharedSyncDebounceTimer;
let applyingSharedState = false;
let eventsBound = false;
let eventsBindingAttempts = 0;
const MAX_EVENT_BIND_ATTEMPTS = 8;
let connectionStatus = 'connecting';
let bootStatusTimer = null;

if (typeof window !== 'undefined') {
    window.__DANBRON_APP_LOADED__ = true;
    if (typeof window.__logBoot === 'function') {
        window.__logBoot('app.js start');
    }
    window.__DANBRON_READY__ = true;
}

// State
const state = {
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
    notes: [],
    habits: [],
    habitLogs: [],
    syncMode: 'backend',
    permissionConsentAccepted: true,
};

// DOM
const $ = (id) => document.getElementById(id);
const on = (id, event, handler) => {
    const el = $(id);
    if (!el) {
        console.warn(`Missing UI control: ${id}`);
        return null;
    }
    const key = `bound${event}`;
    if (!el.dataset[key]) {
        el.addEventListener(event, handler);
        el.dataset[key] = '1';
    }
    return el;
};
const isTauri = () => !!window.__TAURI__;
const getTauriWindow = () => window.__TAURI__?.window?.getCurrentWindow?.();
const invokeTauri = (...args) => {
    const tauri = window.__TAURI__;
    const invoke = tauri?.core?.invoke || tauri?.tauri?.invoke;
    if (!invoke) {
        console.warn('Tauri not available â€” running in browser mode');
        return Promise.resolve(null);
    }
    return invoke(...args);
};

let lastAppErrorNotice = 0;
function notifyAppError(message) {
    const now = Date.now();
    if (now - lastAppErrorNotice < 4000) return;
    lastAppErrorNotice = now;
    if (typeof showNotification === 'function') {
        showNotification(message);
    } else {
        console.error(message);
    }
}

window.addEventListener('error', (event) => {
    if (event?.message) console.error('App error:', event.message);
});

window.addEventListener('unhandledrejection', (event) => {
    const reason = event?.reason?.message || String(event?.reason || 'Error desconocido');
    console.error('Unhandled rejection:', reason);
});

async function nativeWindowAction(action, args = {}) {
    if (!isTauri()) {
        console.warn(`[Browser mode] Window action '${action}' skipped`);
        showNotification('Eso solo funciona en la app instalada.');
        return null;
    }
    try {
        if (action === 'minimize') return await invokeTauri('window_minimize');
        if (action === 'hide') return await invokeTauri('window_hide');
        if (action === 'pin') return await invokeTauri('window_set_always_on_top', args);
    } catch (error) {
        console.error(`Native window action failed: ${action}`, error);
    }
    return null;
}

function nativeMinimize(event) {
    event?.preventDefault?.();
    nativeWindowAction('minimize');
}

function nativeHide(event) {
    event?.preventDefault?.();
    nativeWindowAction('hide');
}

// Init
async function initApp() {
    setupEvents();
    updateConnectionStatus('connecting');
    if (bootStatusTimer) clearTimeout(bootStatusTimer);
    bootStatusTimer = setTimeout(() => {
        if (connectionStatus === 'connecting') {
            updateConnectionStatus('offline');
        }
    }, 8000);

    try {
        // Initialize sync clients
        await initSyncClients();

        loadState();

        await loadBackendSessionFromDisk();
        reconcileBackendSession();
        await continueAfterPermissionGate();
        startMascotAnimation();
        if (state.isPaired) startClipboardSync();
        updateConnectionStatus('online');
    } catch (error) {
        console.warn('Init flow failed:', error);
        notifyAppError(`Fallo de arranque: ${error?.message || error}`);
        updateConnectionStatus('offline');
    } finally {
        if (bootStatusTimer) {
            clearTimeout(bootStatusTimer);
            bootStatusTimer = null;
        }
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
} else {
    initApp();
}

async function initSyncClients() {
    // Import sync client from sync-client.js
    if (typeof DanbronSyncClient !== 'undefined') {
        syncClient = new DanbronSyncClient();
        await configureSyncClientBackend();
        
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
            startClipboardSync();
            showNotification('Tu telefono ya esta conectado.');
        });

        syncClient.on('data_received', (data) => {
            if (data.data) {
                applySharedStateSnapshot(data.data);
                saveState();
            }
        });

        syncClient.on('shared_state_received', (data) => {
            if (data.state) applySharedStateSnapshot(data.state);
        });

        syncClient.on('shared_state_synced', (data) => {
            if (data.state) applySharedStateSnapshot(data.state);
        });

        syncClient.on('event_recorded', (data) => {
            console.log('Event recorded:', data.eventType);
        });

        // Initialize system tracker
        if (typeof WindowsSystemTracker !== 'undefined') {
            systemTracker = new WindowsSystemTracker(syncClient);
        }
        updateOrchestratorBridge();
    }
}

async function configureSyncClientBackend() {
    if (!syncClient || !isTauri()) return;
    try {
        const backendUrl = await invokeTauri('get_backend_url');
        if (backendUrl) syncClient.configureBaseUrl(backendUrl);
    } catch (error) {
        console.debug('Using default backend URL:', error);
    }
}

function updateOrchestratorBridge() {
    if (typeof window === 'undefined') return;
    window.syncClient = syncClient;
    window.systemTracker = systemTracker;
    window.__bronCallAI = async (userMessage) => callAI(userMessage);
    if (window.BronOrchestrator && !window.__DANBRON_ORCHESTRATOR_READY__) {
        window.BronOrchestrator.init(state);
        window.__DANBRON_ORCHESTRATOR_READY__ = true;
    }
}

// ── Clipboard Sync ──
let lastClipboardContent = '';
let clipboardSyncInterval = null;

function startClipboardSync() {
    if (clipboardSyncInterval || !isTauri()) return;
    
    clipboardSyncInterval = setInterval(async () => {
        if (!syncClient?.token || !syncClient?.deviceId || !state.isPaired) return;
        
        try {
            // Read current clipboard via PowerShell
            const currentClip = await invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-Command', 'Get-Clipboard']
            });
            
            const clipText = String(currentClip || '').trim();
            if (clipText && clipText !== lastClipboardContent && clipText.length < 50000) {
                lastClipboardContent = clipText;
                // Push to backend
                await syncClient.post('/sync/clipboard', {
                    deviceId: syncClient.deviceId,
                    content: clipText,
                    contentType: 'text'
                }, syncClient.token);
            }
        } catch (_) {}
        
        // Check if paired device has new clipboard
        try {
            const remote = await syncClient.get('/sync/clipboard', { deviceId: syncClient.deviceId }, syncClient.token);
            if (remote?.content && remote.content !== lastClipboardContent) {
                lastClipboardContent = remote.content;
                // Set local clipboard via PowerShell
                await invokeTauri('run_shell_command', {
                    cmd: 'powershell.exe',
                    args: ['-NoProfile', '-Command', `Set-Clipboard -Value '${remote.content.replace(/'/g, "''")}'`]
                });
                showNotification('Clipboard sincronizado desde tu telefono');
            }
        } catch (_) {}
    }, 3000); // Check every 3 seconds
}

function stopClipboardSync() {
    if (clipboardSyncInterval) {
        clearInterval(clipboardSyncInterval);
        clipboardSyncInterval = null;
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
            
            const shared = await syncClient.getSharedState();
            if (shared) {
                applySharedStateSnapshot(shared);
            } else {
                const data = await syncClient.getSyncedData();
                if (data) applySharedStateSnapshot(data);
            }
            scheduleSharedSync('pair-check');
        } else {
            state.isPaired = false;
            state.pairedDeviceId = '';
            state.pairedDeviceName = '';
            saveState();
        }
    } catch (error) {
        console.error('Failed to check paired device:', error);
    }
}

function toMillis(value, fallback = Date.now()) {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim()) {
        const numeric = Number(value);
        if (Number.isFinite(numeric)) return numeric;
        const parsed = Date.parse(value);
        if (!Number.isNaN(parsed)) return parsed;
    }
    return fallback;
}

function stableNumericId(value) {
    const raw = String(value || `${Date.now()}_${Math.random()}`);
    const numeric = Number(raw);
    if (Number.isSafeInteger(numeric) && numeric > 0) return numeric;
    let hash = 2166136261;
    for (let i = 0; i < raw.length; i += 1) {
        hash ^= raw.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return Math.abs(hash) + 1;
}

function normalizeDesktopNote(note = {}) {
    const createdAt = toMillis(note.createdAt, toMillis(note.updatedAt));
    const updatedAt = toMillis(note.updatedAt, createdAt);
    const id = stableNumericId(note.id || `${note.title || ''}|${note.content || ''}|${createdAt}`);
    return {
        id,
        title: String(note.title || 'Nota rapida').slice(0, 160),
        content: String(note.content || '').slice(0, 5000),
        tag: String(note.tag || 'general').slice(0, 40),
        createdAt,
        updatedAt
    };
}

function normalizeDesktopHabit(habit = {}) {
    const updatedAt = toMillis(habit.updatedAt);
    const id = stableNumericId(habit.id || `${habit.name || ''}|${updatedAt}`);
    return {
        id,
        name: String(habit.name || 'Progreso personal').slice(0, 160),
        streak: Math.max(0, Number(habit.streak || 0) || 0),
        week: Array.isArray(habit.week) ? habit.week.map(Boolean).slice(0, 7) : [false, false, false, false, false, false, false],
        updatedAt
    };
}

function normalizeDesktopHabitLog(log = {}) {
    const date = String(log.date || '').slice(0, 10);
    if (!date) return null;
    return {
        habitId: stableNumericId(log.habitId || log.habit_id || log.id || date),
        date,
        completed: log.completed !== false,
        updatedAt: toMillis(log.updatedAt)
    };
}

function normalizeDesktopMessage(message = {}, fallbackTimestamp = Date.now()) {
    const content = String(message.content || '').trim();
    if (!content) return null;
    return {
        role: message.role === 'assistant' ? 'assistant' : 'user',
        content,
        time: message.time || '',
        timestamp: toMillis(message.timestamp, fallbackTimestamp)
    };
}

function newestBy(items, keyFn, timeFn) {
    const byKey = new Map();
    items.filter(Boolean).forEach(item => {
        const key = String(keyFn(item));
        const current = byKey.get(key);
        if (!current || timeFn(item) >= timeFn(current)) byKey.set(key, item);
    });
    return Array.from(byKey.values());
}

function buildSharedStateSnapshot(includeProfile = false) {
    const now = Date.now();
    const profile = includeProfile && state.userProfile ? {
        ...state.userProfile,
        _syncUpdatedAt: toMillis(state.userProfile._syncUpdatedAt, now)
    } : null;

    const messages = (state.messages || [])
        .map((message, idx, list) => normalizeDesktopMessage(message, now - (list.length - idx) * 1000))
        .filter(Boolean)
        .slice(-80);

    state.messages = messages;

    return {
        schemaVersion: 2,
        source: 'windows',
        updatedAt: now,
        ...(profile ? {
            profileUpdatedAt: profile._syncUpdatedAt || now,
            profile,
            userProfile: profile
        } : {}),
        notes: (state.notes || []).map(normalizeDesktopNote).slice(0, 500),
        habits: (state.habits || []).map(normalizeDesktopHabit).slice(0, 200),
        habitLogs: (state.habitLogs || []).map(normalizeDesktopHabitLog).filter(Boolean).slice(0, 1000),
        tasks: (state.tasks || []).slice(0, 200),
        chatMessages: messages,
        messages
    };
}

function applySharedStateSnapshot(rawSnapshot) {
    const snapshot = rawSnapshot?.sharedState || rawSnapshot?.state || rawSnapshot || {};
    if (!snapshot || typeof snapshot !== 'object') return false;

    applyingSharedState = true;
    try {
        const incomingProfile = snapshot.profile || snapshot.userProfile;
        if (incomingProfile && typeof incomingProfile === 'object') {
            const incomingTime = toMillis(incomingProfile._syncUpdatedAt || snapshot.profileUpdatedAt || snapshot.updatedAt, 0);
            const currentTime = toMillis(state.userProfile?._syncUpdatedAt, 0);
            if (!state.userProfile || incomingTime >= currentTime) {
                state.userProfile = { ...incomingProfile, _syncUpdatedAt: incomingTime || Date.now() };
                state.userName = state.userProfile.name || state.userName;
            }
        }

        const incomingNotes = Array.isArray(snapshot.notes) ? snapshot.notes.map(normalizeDesktopNote) : [];
        if (incomingNotes.length) {
            state.notes = newestBy([...(state.notes || []).map(normalizeDesktopNote), ...incomingNotes], n => n.id, n => n.updatedAt)
                .sort((a, b) => b.updatedAt - a.updatedAt)
                .slice(0, 500);
        }

        const incomingHabits = Array.isArray(snapshot.habits) ? snapshot.habits.map(normalizeDesktopHabit) : [];
        if (incomingHabits.length) {
            state.habits = newestBy([...(state.habits || []).map(normalizeDesktopHabit), ...incomingHabits], h => h.id, h => h.updatedAt)
                .sort((a, b) => b.updatedAt - a.updatedAt)
                .slice(0, 200);
        }

        const incomingLogs = Array.isArray(snapshot.habitLogs) ? snapshot.habitLogs.map(normalizeDesktopHabitLog).filter(Boolean) : [];
        if (incomingLogs.length) {
            state.habitLogs = newestBy([...(state.habitLogs || []).map(normalizeDesktopHabitLog).filter(Boolean), ...incomingLogs], l => `${l.habitId}:${l.date}`, l => l.updatedAt)
                .sort((a, b) => b.updatedAt - a.updatedAt)
                .slice(0, 1000);
        }

        const incomingTasks = Array.isArray(snapshot.tasks) ? snapshot.tasks : [];
        if (incomingTasks.length) {
            const existing = new Map((state.tasks || []).map(t => [t.id, t]));
            incomingTasks.forEach(t => {
                if (!existing.has(t.id)) existing.set(t.id, t);
                else {
                    const local = existing.get(t.id);
                    if ((t.updatedAt || 0) > (local.updatedAt || 0)) existing.set(t.id, t);
                }
            });
            state.tasks = Array.from(existing.values());
        }

        const incomingMessages = Array.isArray(snapshot.chatMessages || snapshot.messages)
            ? (snapshot.chatMessages || snapshot.messages).map(normalizeDesktopMessage).filter(Boolean)
            : [];
        if (incomingMessages.length) {
            state.messages = newestBy([...(state.messages || []).map(normalizeDesktopMessage).filter(Boolean), ...incomingMessages], m => `${m.role}:${m.timestamp}:${m.content}`, m => m.timestamp)
                .sort((a, b) => a.timestamp - b.timestamp)
                .slice(-80);
        }

        saveState();
        renderCommandCenter();
        if ($('dashboardScreen')?.classList.contains('active')) renderDashboard();
        if ($('notesScreen')?.classList.contains('active')) renderNotes();
        if ($('progressScreen')?.classList.contains('active')) renderProgress();
        if ($('profileScreen')?.classList.contains('active')) renderProfile();
        if ($('chatScreen')?.classList.contains('active')) renderMessages();
        return true;
    } finally {
        applyingSharedState = false;
    }
}

function scheduleSharedSync(reason = 'change') {
    if (applyingSharedState || !syncClient || !syncClient.isAuthenticated()) return;
    clearTimeout(sharedSyncDebounceTimer);
    sharedSyncDebounceTimer = setTimeout(() => {
        syncSharedStateNow(reason);
    }, 900);
}

async function syncSharedStateNow(reason = 'manual') {
    if (!syncClient || !syncClient.isAuthenticated()) return false;
    try {
        const includeProfile = /profile|perfil|onboarding/i.test(reason);
        const merged = await syncClient.syncSharedState(buildSharedStateSnapshot(includeProfile));
        if (merged) applySharedStateSnapshot(merged);
        console.debug('Shared state synced:', reason);
        return true;
    } catch (error) {
        console.debug('Shared state sync failed:', error);
        return false;
    }
}

function startSharedStatePolling() {
    if (sharedSyncTimer || !syncClient || !syncClient.isAuthenticated()) return;
    sharedSyncTimer = setInterval(async () => {
        const shared = await syncClient.getSharedState();
        if (shared) applySharedStateSnapshot(shared);
    }, 7000);
    syncSharedStateNow('startup');
}

function stopSharedStatePolling() {
    if (sharedSyncTimer) clearInterval(sharedSyncTimer);
    sharedSyncTimer = null;
    if (sharedSyncDebounceTimer) clearTimeout(sharedSyncDebounceTimer);
    sharedSyncDebounceTimer = null;
}

function setupEvents() {
    if (eventsBound) return;

    // Permissions
    on('acceptPermissionsBtn', 'click', () => acceptMandatoryPermissions());
    on('declinePermissionsBtn', 'click', () => declineMandatoryPermissions());

    // Auth
    on('startBtn', 'click', () => handleAuth());
    on('skipSyncBtn', 'click', () => handleSkipAuth());
    on('pairingBtn', 'click', () => showPairingDialog());

    // Chat
    on('sendBtn', 'click', handleSend);
    on('chatInput', 'keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    });
    on('clearChatBtn', 'click', handleClearChat);
    on('refreshContextBtn', 'click', () => refreshPcContext(true));
    on('voiceBtn', 'click', () => {
        if (voiceState.active) { stopVoiceMode(); } else { startVoiceMode(); }
    });
    on('voiceExitBtn', 'click', () => stopVoiceMode());

    // Settings
    on('settingsBtn', 'click', () => openSettings());
    on('cancelSettingsBtn', 'click', () => closeSettings());

    // Titlebar â€” single binding, no duplicates
    on('pinBtn', 'click', (e) => togglePin(e));
    on('minimizeBtn', 'click', (e) => nativeMinimize(e));
    on('closeBtn', 'click', (e) => nativeHide(e));

    // Screen navigation buttons
    on('contextBtn', 'click', () => showContext());
    on('profileBtn', 'click', () => showProfile());
    on('progressBtn', 'click', () => showProgress());
    on('notesBtn', 'click', () => showNotes());
    on('closeContextBtn', 'click', showChat);
    on('closeProfileBtn', 'click', showChat);
    on('closeProgressBtn', 'click', showChat);
    on('closeNotesBtn', 'click', showChat);
    on('closeSkillsBtn', 'click', showChat);
    on('skillsHeaderBtn', 'click', () => showSkills());

    // Titlebar drag
    const titlebar = $('titlebar');
    if (titlebar) {
        if (!titlebar.dataset.boundMousedown) {
            titlebar.addEventListener('mousedown', (e) => {
                if (e.target.closest('.titlebar-controls')) return;
                const currentWindow = getTauriWindow();
                if (currentWindow) currentWindow.startDragging();
            });
            titlebar.dataset.boundMousedown = '1';
        }
    }

    if (!document.body.dataset.boundDelegatedActions) {
        document.body.addEventListener('click', handleDelegatedAction);
        document.body.dataset.boundDelegatedActions = '1';
    }

    // Close settings modal on overlay click
    const settingsModal = $('settingsModal');
    if (settingsModal) {
        if (!settingsModal.dataset.boundClick) {
            settingsModal.addEventListener('click', (e) => {
                if (e.target === settingsModal) closeSettings();
            });
            settingsModal.dataset.boundClick = '1';
        }
    }

    const requiredIds = [
        'acceptPermissionsBtn', 'declinePermissionsBtn', 'startBtn', 'skipSyncBtn',
        'pairingBtn', 'sendBtn', 'chatInput', 'clearChatBtn', 'refreshContextBtn', 'voiceBtn', 'voiceExitBtn',
        'settingsBtn', 'cancelSettingsBtn', 'pinBtn', 'minimizeBtn', 'closeBtn',
        'contextBtn', 'profileBtn', 'progressBtn', 'notesBtn',
        'closeContextBtn', 'closeProfileBtn', 'closeProgressBtn', 'closeNotesBtn', 'closeSkillsBtn'
    ];
    const missing = requiredIds.filter(id => !$(id));
    if (missing.length > 0) {
        if (eventsBindingAttempts < MAX_EVENT_BIND_ATTEMPTS) {
            eventsBindingAttempts += 1;
            setTimeout(setupEvents, 250);
        } else {
            notifyAppError('Algo no cargo bien. Reinicia la aplicacion.');
        }
        return;
    }

    eventsBound = true;
}

function handleDelegatedAction(event) {
    const button = event.target.closest('[data-action], [data-prompt]');
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();

    const prompt = button.dataset.prompt;
    if (prompt) {
        setPromptAndOpen(prompt);
        return;
    }

    const action = button.dataset.action;
    const actions = {
        dashboard: () => showDashboard(),
        chat: () => showChat(),
        context: () => showContext(),
        profile: () => showProfile(),
        progress: () => showProgress(),
        notes: () => showNotes(),
        skills: () => showSkills(),
        settings: () => openSettings(),
        'refresh-context': () => refreshPcContext(true),
        'add-note': () => addDesktopNote(),
        'save-profile': () => saveProfile(),
        'clear-chat': () => handleClearChat(),
        'confirm-pairing': () => confirmPairingCode(),
        'confirm-email-pairing': () => confirmEmailPairing(),
        'copy-pairing': () => copyPairingCode(),
        'skip-pairing': () => skipPairing()
    };

    if (action?.startsWith('delete-note:')) {
        deleteDesktopNote(Number(action.split(':')[1]));
        return;
    }

    const handler = actions[action];
    if (handler) {
        handler();
    } else {
        console.warn('Unknown delegated action:', action);
    }
}

// Screens
async function continueAfterPermissionGate() {
    if (state.isAuthenticated) {
        await checkPairedDevice();
    }
    showChat();
    if (systemTracker && !systemTracker.isMonitoring) {
        systemTracker.startMonitoring();
    }
    refreshPcContext(false);
    startCommandPolling();
    startSharedStatePolling();
}

function showPermissionsScreen() {
    switchScreen('permissionsScreen');
}

function showSetup() {
    switchScreen('setupScreen');
    const syncCodeInput = $('syncCodeInput');
    if (syncCodeInput) syncCodeInput.value = '';
}

// Central screen switcher â€” hides ALL screens then shows the target
const ALL_SCREENS = ['permissionsScreen', 'setupScreen', 'dashboardScreen', 'chatScreen', 'contextScreen', 'profileScreen', 'progressScreen', 'notesScreen', 'skillsScreen'];
function switchScreen(targetId) {
    ALL_SCREENS.forEach(id => {
        const el = $(id);
        if (el) el.classList.toggle('active', id === targetId);
    });
    syncRailState(targetId);
}

function syncRailState(targetId) {
    document.querySelectorAll('.rail-item').forEach(btn => btn.classList.remove('active'));
    const action = {
        dashboardScreen: 'dashboard',
        chatScreen: 'chat',
        contextScreen: 'context',
        progressScreen: 'progress',
        notesScreen: 'notes',
        profileScreen: 'profile',
        skillsScreen: 'skills'
    }[targetId];
    if (!action) return;
    document.querySelector(`.rail-item[data-action="${action}"]`)?.classList.add('active');
}

function showDashboard() {
    switchScreen('dashboardScreen');
    renderDashboard();
    refreshPcContext(false);
}

function showChat() {
    switchScreen('chatScreen');

    if (state.messages.length === 0) {
        const hour = new Date().getHours();
        const name = state.userName || 'amigo';
        let greeting;
        if (hour >= 5 && hour < 12) {
            greeting = `Buenos dias, ${name}. ¿Como amaneciste? Estoy aqui para lo que necesites hoy.`;
        } else if (hour >= 12 && hour < 19) {
            greeting = `Hola, ${name}. ¿Como va tu dia? Cuentame en que te ayudo.`;
        } else {
            greeting = `Buenas noches, ${name}. Aqui estoy por si necesitas algo antes de descansar.`;
        }
        state.messages.push({
            role: 'assistant',
            content: greeting,
            time: formatTime(),
            timestamp: Date.now()
        });
        saveState();
        // Trigger morning briefing in background
        generateMorningBriefing();
    }

    renderSuggestionChips();
    renderMessages();
    $('chatInput')?.focus();
}

async function generateMorningBriefing() {
    try {
        const hour = new Date().getHours();
        const name = state.userName || 'amigo';
        const today = new Date().toLocaleDateString('es-CL', { weekday: 'long', day: 'numeric', month: 'long' });

        // Gather context
        let context = `Hoy es ${today}.\n`;
        
        // Tasks pending
        const tasks = state.tasks || [];
        const pending = tasks.filter(t => !t.completed);
        if (pending.length > 0) {
            context += `Tareas pendientes: ${pending.map(t => t.title || t.text).join(', ')}.\n`;
        }

        // Habits
        const habits = state.habits || [];
        if (habits.length > 0) {
            context += `Habitos activos: ${habits.map(h => h.name || h.title).join(', ')}.\n`;
        }

        // Notes recent
        const notes = (state.notes || []).slice(-3);
        if (notes.length > 0) {
            context += `Ultimas notas: ${notes.map(n => (n.content || n.text || '').substring(0, 40)).join(' | ')}.\n`;
        }

        // PC context
        if (systemTracker) {
            try {
                const pcCtx = systemTracker.getSnapshot?.() || {};
                if (pcCtx.battery) context += `Bateria: ${pcCtx.battery}%.\n`;
                if (pcCtx.activeWindow) context += `App activa: ${pcCtx.activeWindow}.\n`;
            } catch (_) {}
        }

        // Only do briefing in morning/afternoon first open
        const lastBriefing = localStorage.getItem('danbron_last_briefing_date');
        const todayStr = new Date().toDateString();
        if (lastBriefing === todayStr) return; // Already briefed today
        if (hour < 5 || hour > 14) return; // Only brief in morning

        localStorage.setItem('danbron_last_briefing_date', todayStr);

        // Use AI to generate briefing
        const briefingPrompt = `Eres Bron, asistente personal de ${name}. Genera un BREVE resumen matutino (maximo 3-4 lineas) con tono amigable y natural. Contexto del usuario:\n${context}\nDa un resumen motivador del dia, menciona tareas pendientes si hay, y un tip o animo. NO uses emojis excesivos. Habla como amigo cercano.`;
        
        const briefingResponse = await rawAICall([
            { role: 'system', content: briefingPrompt },
            { role: 'user', content: 'Dame mi briefing de hoy' }
        ]);
        
        if (briefingResponse && briefingResponse.length > 20) {
            state.messages.push({
                role: 'assistant',
                content: `📋 *Tu briefing del dia:*\n\n${briefingResponse}`,
                time: formatTime(),
                timestamp: Date.now()
            });
            saveState();
            renderMessages();
        }
    } catch (e) {
        console.debug('Morning briefing failed:', e);
    }
}

function showContext() {
    switchScreen('contextScreen');
    renderContext();
    refreshPcContext(false);
}

function showProfile() {
    switchScreen('profileScreen');
    renderProfile();
}

function showProgress() {
    switchScreen('progressScreen');
    renderProgress();
}

function showNotes() {
    switchScreen('notesScreen');
    renderNotes();
}

function showSkills() {
    switchScreen('skillsScreen');
    renderSkills();
}

function renderSkills() {
    const skills = typeof SkillRegistry !== 'undefined' ? SkillRegistry.listSkills() : [];
    const skillIcons = {
        'automation': '🤖', 'device-commands': '📱', 'web-workspace': '🌐',
        'weather': '🌤️', 'timer-reminders': '⏰', 'web-search': '🔍',
        'file-manager': '📁', 'music-media': '🎵', 'calculator': '🧮',
        'translator': '🌍', 'system-cleanup': '🧹', 'screenshot-ocr': '📸',
        'social-media': '💬'
    };
    const html = `
        <div style="padding:20px;">
            <p style="color:var(--text-secondary);margin-bottom:20px;">Skills instaladas y funcionando. Puedes activarlas hablando con Bron usando los ejemplos de cada una.</p>
            <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px;">
                ${skills.map(s => `
                    <div style="padding:16px;border-radius:14px;background:var(--bg-secondary);border:1px solid var(--border);">
                        <div style="display:flex;align-items:center;gap:10px;margin-bottom:8px;">
                            <span style="font-size:24px;">${skillIcons[s.id] || '⚡'}</span>
                            <div>
                                <h4 style="margin:0;color:var(--text-primary);">${escapeHtml(s.name)}</h4>
                                <span style="font-size:11px;color:${s.trustLevel === 'trusted' ? '#10B981' : 'var(--text-tertiary)'};">● ${s.trustLevel === 'trusted' ? 'Confiable' : s.source}</span>
                            </div>
                        </div>
                        <p style="margin:0 0 8px;font-size:12px;color:var(--text-secondary);">${escapeHtml(s.description)}</p>
                        <div style="display:flex;flex-wrap:wrap;gap:4px;">
                            ${(s.actions || []).map(a => `<span style="font-size:10px;padding:2px 8px;border-radius:6px;background:rgba(232,184,75,0.1);color:var(--gold);">${a}</span>`).join('')}
                        </div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
    const el = $('skillsContent');
    if (el) el.innerHTML = html;
}

function renderDashboard() {
    const root = $('dashboardContent');
    if (!root) return;

    const profile = state.userProfile || {};
    const notes = Array.isArray(state.notes) ? state.notes : [];
    const habits = Array.isArray(state.habits) ? state.habits : [];
    const income = Number(profile.income || 0);
    const expenses = Number(profile.expenses || 0);
    const debt = Number(profile.debt || 0);
    const free = income - expenses;
    const runway = free > 0 && debt > 0 ? Math.ceil(debt / Math.max(1, free * 0.6)) : 0;
    const name = state.userName || 'amigo';

    const hour = new Date().getHours();
    const greeting = $('dashboardGreeting');
    if (greeting) {
        if (hour >= 5 && hour < 12) greeting.textContent = `Buenos dias, ${name}`;
        else if (hour >= 12 && hour < 19) greeting.textContent = `Buenas tardes, ${name}`;
        else greeting.textContent = `Buenas noches, ${name}`;
    }

    const subtitle = $('dashboardSubtitle');
    if (subtitle) {
        subtitle.textContent = 'Soy Bron, tu amigo personal. Preguntame lo que necesites.';
    }

    root.innerHTML = `
        <section class="dash-panel large">
            <h3>Habla conmigo</h3>
            <p class="dash-muted">Pideme lo que necesites con tus propias palabras: abrir algo, organizar tu dia, recordar una idea, manejar tu dinero o lo que se te ocurra.</p>
            <div class="dash-actions">
                <button class="mini-action" data-action="chat">Hablar con Bron</button>
                <button class="mini-action" data-prompt="Bron, cuentame que tengo pendiente hoy y que deberia hacer primero.">Que hago hoy</button>
            </div>
        </section>

        ${income > 0 || debt > 0 ? `
        <section class="dash-panel medium">
            <h3>Tu dinero</h3>
            <div class="dash-kpi" style="color:${free >= 0 ? 'var(--teal)' : 'var(--red)'}">$${Math.round(free).toLocaleString()}</div>
            <p class="dash-muted">Te queda libre al mes${debt > 0 ? `. Deuda: $${Math.round(debt).toLocaleString()}` : ''}${runway ? ` (puedes salir en ~${runway} meses)` : ''}</p>
            <div class="dash-actions">
                <button class="mini-action" data-prompt="Bron, como van mis finanzas y que puedo mejorar esta semana?">Consejo financiero</button>
                <button class="mini-action" data-action="profile">Actualizar datos</button>
            </div>
        </section>
        ` : `
        <section class="dash-panel medium">
            <h3>Cuentame de ti</h3>
            <p class="dash-muted">Si me dices cuanto ganas, gastas o debes, puedo ayudarte con un plan financiero personalizado.</p>
            <div class="dash-actions">
                <button class="mini-action" data-action="profile">Contarle a Bron</button>
            </div>
        </section>
        `}

        <section class="dash-panel medium">
            <h3>${habits.length ? 'Tus metas' : 'Crea una meta'}</h3>
            ${habits.length ? `
                <div class="dash-kpi">${habits.length}</div>
                <p class="dash-muted">metas activas. Dime "marca progreso" para avanzar.</p>
            ` : `
                <p class="dash-muted">Dime algo como "Bron, quiero empezar a ahorrar cada dia" y te creo una meta para seguir tu avance.</p>
            `}
            <div class="dash-actions">
                <button class="mini-action" data-action="progress">Ver mis metas</button>
                <button class="mini-action" data-prompt="Bron, ayudame a crear una meta nueva para mejorar mi vida.">Nueva meta</button>
            </div>
        </section>

        <section class="dash-panel medium">
            <h3>Tus ideas</h3>
            ${notes.length ? dashboardList(notes.slice(0, 4).map(n => [n.title || 'Idea', n.content || '']), '') : `
                <p class="dash-muted">Aqui aparecen tus notas e ideas. Dime "Bron, recuerdame que..." y lo guardo.</p>
            `}
            <div class="dash-actions">
                <button class="mini-action" data-action="notes">Ver ideas</button>
                <button class="mini-action" data-prompt="Bron, quiero anotar algo importante.">Anotar idea</button>
            </div>
        </section>

        <section class="dash-panel full">
            <h3>Prueba decirme algo asi</h3>
            <div class="dash-actions">
                <button class="mini-action" data-prompt="Bron, organizame el dia de hoy paso a paso.">Organizar mi dia</button>
                <button class="mini-action" data-prompt="Bron, dame un consejo para ganar mas dinero esta semana.">Consejo de dinero</button>
                <button class="mini-action" data-prompt="Bron, recuerdame investigar como automatizar mi trabajo.">Guardar una idea</button>
                <button class="mini-action" data-prompt="Bron, ayudame a enfocarme los proximos 30 minutos.">Enfocarme</button>
                <button class="mini-action" data-prompt="Bron, que clima hace hoy?">Clima</button>
                <button class="mini-action" data-prompt="Bron, pon un timer de 25 minutos para trabajar.">Timer 25min</button>
                <button class="mini-action" data-prompt="Bron, limpia los archivos temporales de mi PC.">Limpiar PC</button>
                ${state.isPaired ? '<button class="mini-action" data-prompt="Bron, abre YouTube en mi telefono.">Algo en mi telefono</button>' : ''}
            </div>
        </section>

        <section class="dash-panel">
            <h3>⚡ Skills activas: ${typeof SkillRegistry !== 'undefined' ? SkillRegistry.listSkills().length : 0}</h3>
            <p class="dash-muted">Clima, timer, buscador, archivos, musica, calculadora, traductor, limpieza, captura y redes sociales.</p>
            <div class="dash-actions">
                <button class="mini-action" data-action="skills">Ver todas</button>
            </div>
        </section>
    `;
}

function dashboardList(rows, empty) {
    if (!rows.length) return `<p class="dash-muted">${escapeHtml(empty)}</p>`;
    return `<ul class="dash-list">${rows.map(([left, right]) => `
        <li><span>${escapeHtml(String(left || ''))}</span><strong>${escapeHtml(String(right || '')).slice(0, 120)}</strong></li>
    `).join('')}</ul>`;
}

function renderSuggestionChips() {
    const el = $('suggestionChips');
    if (!el) return;

    const hour = new Date().getHours();
    const chips = [];

    if (hour >= 5 && hour < 12) {
        chips.push({ emoji: '☀️', text: 'Como empieza mi dia', prompt: 'Bron, cuentame que tengo pendiente hoy y que deberia hacer primero.' });
        chips.push({ emoji: '📋', text: 'Organizar mis tareas', prompt: 'Bron, ayudame a organizar lo que tengo que hacer hoy paso a paso.' });
    } else if (hour >= 12 && hour < 19) {
        chips.push({ emoji: '🎯', text: 'En que me enfoco ahora', prompt: 'Bron, ayudame a enfocarme los proximos 30 minutos. No inventes.' });
        chips.push({ emoji: '💡', text: 'Dame una idea', prompt: 'Bron, dame una idea concreta para avanzar hoy con mis metas.' });
    } else {
        chips.push({ emoji: '🌙', text: 'Resumen de mi dia', prompt: 'Bron, hazme un resumen de como fue mi dia y que queda pendiente.' });
        chips.push({ emoji: '📝', text: 'Anotar algo', prompt: 'Bron, quiero guardar una nota rapida.' });
    }

    chips.push({ emoji: '💰', text: 'Mis finanzas', prompt: 'Bron, como van mis finanzas y que puedo mejorar esta semana?' });
    chips.push({ emoji: '🖥️', text: 'Que tengo abierto', prompt: 'Bron, dime que aplicaciones tengo abiertas ahora mismo en mi computador.' });
    chips.push({ emoji: '🌤️', text: 'Clima ahora', prompt: 'Bron, que clima hace ahora mismo?' });
    chips.push({ emoji: '⏰', text: 'Timer 25 min', prompt: 'Bron, pon un timer de 25 minutos para trabajar concentrado.' });
    chips.push({ emoji: '🧹', text: 'Limpiar PC', prompt: 'Bron, limpia los archivos temporales de mi PC y dime cuanto espacio se libero.' });

    if (state.isPaired) {
        chips.push({ emoji: '📱', text: 'Algo en mi telefono', prompt: 'Bron, abre YouTube en mi telefono.' });
    }

    el.innerHTML = chips.map(c =>
        `<button class="suggestion-chip" data-prompt="${escapeAttr(c.prompt)}"><span class="chip-emoji">${c.emoji}</span>${escapeHtml(c.text)}</button>`
    ).join('');
}

function renderCommandCenter() {
    renderSuggestionChips();
}

function setPrompt(text) {
    const input = $('chatInput');
    if (!input) return;
    input.value = text;
    input.focus();
}

function setPromptAndOpen(text) {
    showChat();
    setPrompt(text);
}

function renderProfile() {
    const u = state.userProfile || {};
    const income = Number(u.income || 0);
    const expenses = Number(u.expenses || 0);
    const debt = Number(u.debt || 0);
    const free = income - expenses;
    const html = `
        <div class="profile-summary-grid">
            <div class="metric-tile"><label>Ingresos</label><div>$${income.toLocaleString()}</div></div>
            <div class="metric-tile"><label>Gastos</label><div>$${expenses.toLocaleString()}</div></div>
            <div class="metric-tile"><label>Flujo libre</label><div style="color:${free >= 0 ? 'var(--teal)' : 'var(--red)'}">$${free.toLocaleString()}</div></div>
        </div>
        <div class="profile-field">
            <label>Nombre</label>
            <input type="text" value="${escapeAttr(u.name || state.userName || '')}" placeholder="Tu nombre">
        </div>
        <div class="profile-field">
            <label>Email</label>
            <input type="email" value="${escapeAttr(state.email || '')}" disabled>
        </div>
        <div class="profile-field">
            <label>Ingresos Mensuales ($)</label>
            <input type="number" value="${escapeAttr(u.income ?? '')}" placeholder="0">
        </div>
        <div class="profile-field">
            <label>Gastos Mensuales ($)</label>
            <input type="number" value="${escapeAttr(u.expenses ?? '')}" placeholder="0">
        </div>
        <div class="profile-field">
            <label>Deuda Total ($)</label>
            <input type="number" value="${escapeAttr(u.debt ?? '')}" placeholder="0">
        </div>
        <div class="profile-field">
            <label>Trabajo/Rutina</label>
            <input type="text" value="${escapeAttr(u.workStyle || '')}" placeholder="driver, freelance, full_time">
        </div>
        <div class="profile-field">
            <label>Contexto para Bron</label>
            <textarea id="desktopLifeSummary" placeholder="Ej: Soy chofer de Uber y quiero controlar ganancias, viajes y gastos.">${escapeHtml(u.lifeSummary || '')}</textarea>
        </div>
        <div class="screen-actions">
            <button class="btn-ghost" data-action="chat">Cancelar</button>
            <button class="btn-gold" data-action="save-profile">Guardar Perfil</button>
        </div>
    `;
    $('profileContent').innerHTML = html;
}

function renderProgress() {
    const u = state.userProfile || {};
    const debt = u.debt || 0;
    const income = u.income || 0;
    const expenses = u.expenses || 0;
    const free = income - expenses;
    const monthly = free > 0 ? free * 0.6 : 0;
    const pct = debt > 0 ? Math.min(100, Math.max(2, (monthly / debt) * 100)) : 100;
    const monthsLeft = debt > 0 && monthly > 0 ? Math.ceil(debt / monthly) : 0;
    const habits = Array.isArray(state.habits) ? state.habits : [];
    const logs = Array.isArray(state.habitLogs) ? state.habitLogs : [];
    const tasks = Array.isArray(state.tasks) ? state.tasks : [];
    const notes = Array.isArray(state.notes) ? state.notes : [];
    const today = new Date().toISOString().slice(0, 10);
    const doneToday = logs.filter(log => log.date === today && log.completed !== false).length;
    const tasksDone = tasks.filter(t => t.done).length;
    const days = ['L','M','X','J','V','S','D'];
    const totalWeekChecks = habits.reduce((sum, h) => sum + (Array.isArray(h.week) ? h.week.filter(Boolean).length : 0), 0);
    const weekConsistency = habits.length > 0 ? Math.min(100, Math.round((totalWeekChecks / (habits.length * 7)) * 100)) : 0;
    const consistencyColor = weekConsistency >= 70 ? '#10B981' : weekConsistency >= 40 ? 'var(--gold)' : 'var(--red)';
    
    let html = `
        <!-- Quick stats -->
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:16px;">
            <div class="progress-card" style="text-align:center;padding:14px;">
                <p style="font-size:22px;font-weight:700;color:var(--gold);margin:0;">${tasksDone}/${tasks.length}</p>
                <p style="font-size:11px;color:var(--text-tertiary);margin:4px 0 0;">Tareas hoy</p>
            </div>
            <div class="progress-card" style="text-align:center;padding:14px;">
                <p style="font-size:22px;font-weight:700;color:#10B981;margin:0;">${doneToday}/${habits.length}</p>
                <p style="font-size:11px;color:var(--text-tertiary);margin:4px 0 0;">Habitos hoy</p>
            </div>
            <div class="progress-card" style="text-align:center;padding:14px;">
                <p style="font-size:22px;font-weight:700;color:#9B59B6;margin:0;">${notes.length}</p>
                <p style="font-size:11px;color:var(--text-tertiary);margin:4px 0 0;">Notas</p>
            </div>
            <div class="progress-card" style="text-align:center;padding:14px;">
                <p style="font-size:22px;font-weight:700;color:${consistencyColor};margin:0;">${weekConsistency}%</p>
                <p style="font-size:11px;color:var(--text-tertiary);margin:4px 0 0;">Consistencia</p>
            </div>
        </div>

        <!-- Consistency bar -->
        <div class="progress-card" style="margin-bottom:14px;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                <h3 style="margin:0;">Consistencia semanal</h3>
                <span style="color:${consistencyColor};font-weight:700;">${weekConsistency}%</span>
            </div>
            <div class="progress-bar"><div class="progress-fill" style="width:${weekConsistency}%;background:${consistencyColor};"></div></div>
            <p style="font-size:11px;color:var(--text-tertiary);margin:6px 0 0;">${weekConsistency >= 70 ? 'Excelente! Muy constante' : weekConsistency >= 40 ? 'Vas bien, sigue asi' : 'Puedes mejorar. Un habito a la vez'}</p>
        </div>
    `;

    // Debt section
    if (debt > 0) {
        html += `
        <div class="progress-card" style="margin-bottom:14px;">
            <h3>Estado de deuda</h3>
            <p style="font-size:28px;font-weight:700;color:var(--red);margin:4px 0;">$${debt.toLocaleString()}</p>
            <p style="color:var(--text-tertiary);font-size:12px;">Pagando $${monthly.toLocaleString()}/mes${monthsLeft > 0 ? ' · ~' + monthsLeft + ' meses restantes' : ''}</p>
            <div class="progress-bar" style="margin-top:10px;"><div class="progress-fill" style="width:${pct}%;"></div></div>
        </div>`;
    }

    // Habits detail
    html += `<h3 style="margin:14px 0 10px;">Habitos activos</h3>`;
    if (habits.length) {
        html += '<div class="notes-grid">';
        habits.forEach(habit => {
            const week = Array.isArray(habit.week) ? habit.week : [false,false,false,false,false,false,false];
            const weekHtml = days.map((d, i) => {
                const done = week[i] || false;
                return `<div style="width:24px;height:24px;border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:${done?700:400};background:${done?'var(--gold)':'var(--bg-tertiary)'};color:${done?'#0A0A0F':'var(--text-tertiary)'};border:1px solid ${done?'var(--gold)':'var(--border)'};">${d}</div>`;
            }).join('');
            html += `
                <div class="note-item">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                        <h4 style="margin:0;">${escapeHtml(habit.name || 'Habito')}</h4>
                        ${habit.streak > 0 ? `<span style="color:var(--gold);font-size:12px;font-weight:600;">🔥 ${habit.streak}d</span>` : ''}
                    </div>
                    <div style="display:flex;gap:4px;">${weekHtml}</div>
                </div>`;
        });
        html += '</div>';
    } else {
        html += `<div class="empty-state"><p>Sin habitos aun. Dile a Bron: "agregame un habito".</p></div>`;
    }

    // Sync status
    if (state.isPaired) {
        html += `<div style="margin-top:14px;padding:10px 14px;background:rgba(16,185,129,0.06);border:1px solid rgba(16,185,129,0.2);border-radius:10px;">
            <p style="font-size:12px;color:#10B981;margin:0;">🔗 Sincronizado con tu telefono · Habitos y progreso se comparten automaticamente</p>
        </div>`;
    }

    $('progressContent').innerHTML = html;
}

function renderContext() {
    const ctx = state.systemContext || {};
    const windows = ctx.activeWindows || [];
    const apps = ctx.installedApps || [];
    const games = ctx.games || [];
    const comms = ctx.comms || [];
    const battery = ctx.battery;
    const updated = ctx.lastUpdated ? new Date(ctx.lastUpdated).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : 'sin actualizar';

    $('contextContent').innerHTML = `
        <div class="context-hero">
            <div>
                <p class="eyebrow">Lo que Bron puede ver</p>
                <h3>${windows.length} cosas abiertas</h3>
                <p>Ultima vez: ${updated}</p>
            </div>
            <button class="btn-gold context-refresh" data-action="refresh-context">Mirar de nuevo</button>
        </div>
        <div class="context-grid">
            ${contextSection('Lo que tienes abierto', windows.slice(0, 8).map(w => `${w.processName}: ${w.windowTitle}`), 'No veo nada abierto ahora.')}
            ${contextSection('Mensajes y comunicacion', comms.slice(0, 8).map(c => `${c.source}: ${c.title}`), 'No veo WhatsApp, Gmail u otra app de mensajes.')}
            ${contextSection('Entretenimiento', games.slice(0, 8).map(g => `${g.name}${g.running ? ' (abierto ahora)' : ''}`), 'No veo juegos ni videos abiertos.')}
            ${contextSection('Apps en tu computador', apps.slice(0, 12).map(a => a.name), 'Todavia no pude ver tus apps.')}
            ${contextSection('Tu equipo', [
                ctx.systemInfo ? `${ctx.systemInfo.computerName || 'Tu PC'} / ${ctx.systemInfo.osVersion || 'Windows'}` : null,
                battery ? `Bateria: ${battery.level || 100}%` : null
            ].filter(Boolean), 'Sin datos del equipo.')}
        </div>
        <div class="context-note">
            Bron puede ver las aplicaciones que tienes abiertas, sus nombres y la bateria de tu equipo. No puede leer contrasenas, mensajes privados ni informacion que no este visible en pantalla.
        </div>
    `;
}

function contextSection(title, items, emptyText) {
    const body = items.length
        ? items.map(item => `<li>${escapeHtml(String(item))}</li>`).join('')
        : `<li class="muted">${emptyText}</li>`;
    return `<section class="context-card"><h4>${title}</h4><ul>${body}</ul></section>`;
}

function escapeHtml(value) {
    return value.replace(/[&<>"']/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
    }[char]));
}

function escapeAttr(value) {
    return escapeHtml(String(value ?? ''));
}

async function refreshPcContext(forceToast = false) {
    if (!state.permissionConsentAccepted) return false;
    if (!systemTracker || typeof systemTracker.getFullContext !== 'function') {
        if (forceToast) showNotification('Todavia no puedo ver tu computador.');
        return false;
    }

    try {
        if (forceToast) showNotification('Mirando que tienes abierto...');
        state.systemContext = await systemTracker.getFullContext();
        if (!Array.isArray(state.systemContext.activeWindows) || state.systemContext.activeWindows.length === 0) {
            const fallbackWindows = await readVisibleWindowsFallback();
            if (fallbackWindows.length) {
                state.systemContext.activeWindows = fallbackWindows;
                state.systemContext.comms = await inferCommunicationSignals(fallbackWindows);
                state.systemContext.games = inferGamesAndDistractions(fallbackWindows, state.systemContext.installedApps || []);
            }
        }
        state.systemContext.lastError = null;
        saveState();
        renderCommandCenter();
        if ($('dashboardScreen')?.classList.contains('active')) renderDashboard();
        if ($('contextScreen')?.classList.contains('active')) renderContext();
        if (forceToast) showNotification('Listo, ya vi lo que tienes abierto.');
        return true;
    } catch (error) {
        console.warn('PC context refresh failed:', error);
        state.systemContext = {
            activeWindows: [],
            installedApps: [],
            comms: [],
            games: [],
            battery: null,
            lastUpdated: new Date().toISOString(),
            lastError: error.message || 'No pude leer el contexto de Windows'
        };
        saveState();
        if (forceToast) showNotification('No pude ver lo que tienes abierto ahora.');
        return false;
    }
}

async function readVisibleWindowsFallback() {
    if (!isTauri()) return [];

    const command = `
        $ErrorActionPreference = 'SilentlyContinue'
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        Get-Process |
          Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.Trim().Length -gt 0 } |
          Select-Object -First 40 ProcessName, MainWindowTitle |
          ConvertTo-Json -Depth 3 -Compress
    `;

    try {
        const output = await invokeTauri('run_shell_command', {
            cmd: 'powershell.exe',
            args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command]
        });
        if (!output || !String(output).trim()) return [];
        const parsed = JSON.parse(String(output).trim());
        const list = Array.isArray(parsed) ? parsed : [parsed];
        return list
            .map(w => ({
                processName: String(w.ProcessName || '').trim(),
                windowTitle: String(w.MainWindowTitle || '').trim().slice(0, 180)
            }))
            .filter(w => w.processName && w.windowTitle);
    } catch (error) {
        console.warn('Visible windows fallback failed:', error);
        return [];
    }
}

async function inferCommunicationSignals(activeWindows = []) {
    const known = [
        ['whatsapp', 'WhatsApp'],
        ['gmail', 'Gmail'],
        ['outlook', 'Outlook'],
        ['mail', 'Correo'],
        ['telegram', 'Telegram'],
        ['discord', 'Discord'],
        ['teams', 'Teams'],
        ['slack', 'Slack']
    ];

    return activeWindows
        .map(w => {
            const haystack = `${w.processName} ${w.windowTitle}`.toLowerCase();
            const match = known.find(([needle]) => haystack.includes(needle));
            return match ? { source: match[1], title: w.windowTitle, processName: w.processName } : null;
        })
        .filter(Boolean);
}

function inferGamesAndDistractions(activeWindows = [], installedApps = []) {
    const needles = [
        'steam', 'epic games', 'xbox', 'riot', 'valorant', 'league of legends',
        'minecraft', 'roblox', 'battle.net', 'ea app', 'ubisoft', 'rockstar',
        'fortnite', 'counter-strike', 'cs2', 'gta', 'call of duty', 'youtube'
    ];

    const running = activeWindows
        .filter(w => needles.some(n => `${w.processName} ${w.windowTitle}`.toLowerCase().includes(n)))
        .map(w => ({ name: w.windowTitle || w.processName, running: true }));

    const installed = installedApps
        .filter(app => needles.some(n => String(app.name || '').toLowerCase().includes(n)))
        .map(app => ({ name: app.name, running: false }));

    const byName = new Map();
    [...running, ...installed].forEach(item => {
        const key = String(item.name || '').toLowerCase();
        if (key && (!byName.has(key) || item.running)) byName.set(key, item);
    });
    return Array.from(byName.values()).slice(0, 30);
}

function renderNotes() {
    const notes = state.notes || [];
    const tagIcons = { finance: '💰', goal: '🎯', idea: '💡', general: '📝' };
    const tagLabels = { finance: 'Finanzas', goal: 'Metas', idea: 'Ideas', general: 'General' };

    let html = `
        <div class="note-composer">
            <div style="display:flex;gap:8px;margin-bottom:8px;">
                <input id="newNoteTitle" placeholder="Titulo" style="flex:1;">
                <select id="newNoteTag" style="background:var(--bg-tertiary);border:1px solid var(--border);border-radius:8px;padding:6px 10px;color:var(--text-primary);font-size:12px;">
                    <option value="general">📝 General</option>
                    <option value="finance">💰 Finanzas</option>
                    <option value="goal">🎯 Metas</option>
                    <option value="idea">💡 Ideas</option>
                </select>
            </div>
            <textarea id="newNoteBody" placeholder="Escribe una nota rapida..."></textarea>
            <button class="btn-gold" data-action="add-note">Agregar nota</button>
        </div>
    `;

    // Stats bar
    html += `<div style="display:flex;gap:12px;margin:12px 0;padding:10px 14px;background:var(--bg-secondary);border-radius:10px;border:1px solid var(--border);">
        <span style="color:var(--gold);font-size:13px;font-weight:600;">${notes.length} notas</span>
        <span style="color:var(--text-tertiary);font-size:12px;">📝 ${notes.filter(n => !n.tag || n.tag === 'general').length}</span>
        <span style="color:var(--text-tertiary);font-size:12px;">💰 ${notes.filter(n => n.tag === 'finance').length}</span>
        <span style="color:var(--text-tertiary);font-size:12px;">🎯 ${notes.filter(n => n.tag === 'goal').length}</span>
        <span style="color:var(--text-tertiary);font-size:12px;">💡 ${notes.filter(n => n.tag === 'idea').length}</span>
        ${state.isPaired ? '<span style="color:#10B981;font-size:11px;margin-left:auto;">🔗 Sincronizadas</span>' : ''}
    </div>`;

    if (notes.length === 0) {
        html += `
            <div class="empty-state">
                <p style="font-size:32px;">📭</p>
                <p>No hay notas aun</p>
                <p style="font-size:12px;color:var(--text-tertiary);">Tus ideas y recordatorios apareceran aqui. Dile a Bron "recuerdame que..."</p>
            </div>
        `;
        $('notesContent').innerHTML = html;
        return;
    }

    html += '<div class="notes-grid">';
    notes.forEach((note, idx) => {
        const tag = note.tag || 'general';
        const icon = tagIcons[tag] || '📝';
        const dateStr = note.updatedAt ? new Date(note.updatedAt).toLocaleDateString('es', {day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}) : '';
        html += `
            <div class="note-item" style="position:relative;">
                <div style="display:flex;align-items:center;gap:6px;margin-bottom:6px;">
                    <span style="font-size:14px;">${icon}</span>
                    <h4 style="flex:1;margin:0;">${escapeHtml(String(note.title || 'Sin titulo'))}</h4>
                    <button class="note-delete" data-action="delete-note:${idx}" style="font-size:11px;opacity:0.5;">✕</button>
                </div>
                <p style="margin:0 0 8px;">${escapeHtml(String(note.content || ''))}</p>
                <div style="display:flex;justify-content:space-between;align-items:center;">
                    <span style="font-size:10px;color:var(--text-tertiary);background:var(--bg-tertiary);padding:2px 8px;border-radius:6px;">${tagLabels[tag] || tag}</span>
                    <span style="font-size:10px;color:var(--text-tertiary);">${dateStr}</span>
                </div>
            </div>
        `;
    });
    html += '</div>';
    $('notesContent').innerHTML = html;
}

function addDesktopNote() {
    const title = $('newNoteTitle')?.value?.trim() || 'Sin titulo';
    const content = $('newNoteBody')?.value?.trim();
    const tag = $('newNoteTag')?.value || 'general';
    if (!content) {
        showNotification('Escribe algo para guardar la nota.');
        return;
    }

    state.notes = [
        { id: Date.now(), title, content, tag, createdAt: Date.now(), updatedAt: Date.now() },
        ...(state.notes || [])
    ];
    saveState();
    scheduleSharedSync('note-added');
    renderNotes();
    if ($('dashboardScreen')?.classList.contains('active')) renderDashboard();
    showNotification('Nota agregada.');
}

function saveProfile() {
    const inputs = $('profileContent').querySelectorAll('input');
    const updated = {
        ...state.userProfile,
        name: inputs[0].value,
        income: parseFloat(inputs[2].value) || 0,
        expenses: parseFloat(inputs[3].value) || 0,
        debt: parseFloat(inputs[4].value) || 0,
        workStyle: inputs[5]?.value || state.userProfile?.workStyle || '',
        lifeSummary: $('desktopLifeSummary')?.value || state.userProfile?.lifeSummary || '',
        _syncUpdatedAt: Date.now()
    };
    state.userProfile = updated;
    saveState();
    scheduleSharedSync('profile-saved');
    renderCommandCenter();
    if ($('dashboardScreen')?.classList.contains('active')) renderDashboard();
    showNotification('Perfil guardado.');
    showDashboard();
}

function deleteDesktopNote(idx) {
    state.notes.splice(idx, 1);
    saveState();
    scheduleSharedSync('note-deleted');
    renderNotes();
    if ($('dashboardScreen')?.classList.contains('active')) renderDashboard();
    showNotification('Nota eliminada');
}

// Handlers
async function acceptMandatoryPermissions() {
    const accessConsent = $('pcAccessConsent')?.checked;
    const syncConsent = $('pcSyncConsent')?.checked;

    if (!accessConsent || !syncConsent) {
        setPermissionStatus('Necesito ambos permisos para poder ayudarte.');
        return;
    }

    state.permissionConsentAccepted = true;
    saveState();
    setPermissionStatus('');
    showNotification('Listo, ya puedo ayudarte.');
    await continueAfterPermissionGate();
}
window.acceptMandatoryPermissions = acceptMandatoryPermissions;

async function declineMandatoryPermissions() {
    setPermissionStatus('Sin permisos no puedo ayudarte como necesitas.');
    try {
        const currentWindow = getTauriWindow();
        if (currentWindow) {
            await currentWindow.close();
        }
    } catch (_) {
        document.body.innerHTML = '<div style="padding:32px;color:#F0EDE6;font-family:sans-serif">Bron se cerro. Necesita permisos para funcionar.</div>';
    }
}
window.declineMandatoryPermissions = declineMandatoryPermissions;

function setPermissionStatus(message) {
    const el = $('permissionStatus');
    if (!el) return;
    el.textContent = message;
    el.classList.toggle('active', Boolean(message));
}

async function handleAuth() {
    const email = $('emailInput')?.value?.trim();
    
    if (!email) {
        showNotification('Por favor ingresa tu email');
        return;
    }

    if (!syncClient) {
        showNotification('No puedo conectarme ahora');
        return;
    }

    $('startBtn').textContent = "Conectando...";
    $('startBtn').disabled = true;

    try {
        await syncClient.authenticate(email);
        await syncClient.registerDevice('Danbron Desktop');
        
        state.isAuthenticated = true;
        state.email = email;
        state.deviceId = syncClient.deviceId;
        
        saveState();
        setSetupStatus('');
        showPairingDialog(false);
    } catch (error) {
        state.email = email;
        state.userName = email.split('@')[0];
        state.userId = 'offline_' + Math.random().toString(36).substring(2, 9);
        state.isAuthenticated = false;
        state.syncMode = 'offline';
        saveState();
        setSetupStatus('No hay conexion ahora. Puedes vincular tu telefono despues.', true);
        showPairingDialog(true);
        showNotification('Sin conexion. Puedes empezar igual.');
    } finally {
        $('startBtn').textContent = "Conectar";
        $('startBtn').disabled = false;
    }
}
window.handleAuth = handleAuth;

function handleSkipAuth() {
    state.userId = 'local_' + Math.random().toString(36).substring(2, 9);
    state.userName = 'Amigo';
    state.userProfile = null;
    state.messages = [];
    state.isAuthenticated = false;
    saveState();
    if (systemTracker && !systemTracker.isMonitoring) {
        systemTracker.startMonitoring();
    }
    refreshPcContext(false);
    showDashboard();
}
window.handleSkipAuth = handleSkipAuth;

async function showPairingDialog(offlineMode = false) {
    // Remove existing modal if present
    const $existing = document.getElementById('pairingModal');
    if ($existing) $existing.remove();
    
    const $pairingModal = document.createElement('div');
    $pairingModal.className = 'modal-overlay active';
    $pairingModal.id = 'pairingModal';
    $pairingModal.innerHTML = `
        <div class="modal-content">
            <h2>Vincular tu telefono</h2>
            <div class="pairing-options">
                <div class="pairing-option" style="flex:1">
                    <h3>Vincular con tu email</h3>
                    <p>Usa el mismo email en tu telefono y aqui. Asi se conectan automaticamente.</p>
                    <input type="email" id="pairingEmailInput" placeholder="tu@gmail.com" style="width:100%;padding:10px;border-radius:8px;border:1px solid #333;background:#111;color:#fff;font-size:15px;">
                    <button data-action="confirm-email-pairing" style="margin-top:10px;width:100%">Vincular</button>
                    <p id="emailPairStatus" style="margin-top:8px;font-size:13px;color:#888;min-height:18px;"></p>
                </div>
                <div class="pairing-divider">O</div>
                <div class="pairing-option" style="flex:1">
                    <h3>Tengo un codigo de mi telefono</h3>
                    <p>Abre Bron en tu telefono, ve a tu perfil y busca "Vincular con PC".</p>
                    <input type="text" id="pairingCodeInput" placeholder="Codigo de 6 numeros" maxlength="6">
                    <button data-action="confirm-pairing">Listo</button>
                </div>
            </div>
            <button class="btn-secondary" data-action="skip-pairing">Seguir sin telefono</button>
        </div>
    `;
    
    document.body.appendChild($pairingModal);

    // Pre-fill email if we have one
    if (state.email) {
        const emailInput = $('pairingEmailInput');
        if (emailInput) emailInput.value = state.email;
    }
}

async function confirmEmailPairing() {
    const email = $('pairingEmailInput')?.value?.trim();
    const statusEl = $('emailPairStatus');

    if (!email || !email.includes('@')) {
        if (statusEl) statusEl.textContent = 'Escribe un email valido';
        return;
    }

    const btn = document.querySelector('[data-action="confirm-email-pairing"]');
    if (btn) { btn.textContent = 'Conectando...'; btn.disabled = true; }
    if (statusEl) statusEl.textContent = 'Autenticando...';

    try {
        // Step 1: Authenticate with this email
        await syncClient.authenticate(email, state.userName || 'Desktop');
        state.email = email;
        state.isAuthenticated = true;
        saveState();

        // Step 2: Register this device if needed
        if (!syncClient.deviceId) {
            await syncClient.registerDevice('Danbron Desktop');
        }
        if (statusEl) statusEl.textContent = 'Buscando tu telefono...';

        // Step 3: Try auto-pair (finds android device under same account)
        const result = await syncClient.autoPair();

        if (result && result.paired) {
            state.isPaired = true;
            state.syncMode = 'backend';
            state.pairedDeviceName = result.otherDevice?.device_name || 'Android';
            saveState();

            const $modal = $('pairingModal');
            if ($modal) $modal.remove();

            showChat();
            showNotification('Vinculado con tu telefono. Listo!');
            return;
        }

        // No other device found yet — that's ok, wait for Android to register
        if (statusEl) {
            statusEl.style.color = '#d4a54a';
            statusEl.textContent = 'Listo! Ahora abre Bron en tu telefono, ve a Ajustes > Vincular con PC, y escribe este mismo email: ' + email;
        }
    } catch (error) {
        console.error('Email pairing failed:', error);
        if (statusEl) {
            statusEl.style.color = '#e74c3c';
            statusEl.textContent = 'Error: ' + (error.message || 'No se pudo conectar al servidor');
        }
    } finally {
        if (btn) { btn.textContent = 'Vincular'; btn.disabled = false; }
    }
}

async function confirmPairingCode() {
    const code = $('pairingCodeInput')?.value?.trim();
    
    if (!code || !/^\d{6}$/.test(code)) {
        showNotification('Codigo debe ser exactamente 6 digitos (0-9)');
        return;
    }

    // Show loading state
    const confirmBtn = document.querySelector('[data-action="confirm-pairing"]');
    if (confirmBtn) { confirmBtn.textContent = 'Buscando...'; confirmBtn.disabled = true; }

    try {
        // Ensure we're authenticated before anything
        if (!syncClient?.token && syncClient) {
            try {
                const email = state.email || `desktop_${(navigator.userAgent.match(/Windows[^;)]*/)?.[0] || 'PC').replace(/\s+/g, '_')}@danbron.app`;
                await syncClient.authenticate(email, state.userName || 'Desktop');
                if (!syncClient.deviceId) await syncClient.registerDevice('Danbron Desktop');
                state.isAuthenticated = true;
                state.email = email;
                saveState();
            } catch(_) {}
        }
        if (syncClient?.token && !syncClient?.deviceId) {
            try { await syncClient.registerDevice('Danbron Desktop'); } catch(_) {}
        }

        // Try 1: Fetch profile from ntfy.sh via JS fetch
        let mobileProfile = await fetchMobileProfileByCode(code);
        
        // Try 2: If JS fetch failed, try via Rust proxy (bypasses CSP/CORS)
        if (!mobileProfile && isTauri()) {
            try {
                const rawResult = await invokeTauri('run_shell_command', {
                    cmd: 'powershell.exe',
                    args: ['-NoProfile', '-Command', `(Invoke-WebRequest -Uri "https://ntfy.sh/danbron_sync_${code}/json?poll=1&since=24h" -TimeoutSec 10).Content`]
                });
                const lines = String(rawResult || '').split(/\r?\n/).filter(Boolean).reverse();
                for (const line of lines) {
                    try {
                        const event = JSON.parse(line);
                        const raw = event.message || event.content || event.data;
                        if (!raw) continue;
                        const profile = typeof raw === 'string' ? JSON.parse(raw) : raw;
                        if (profile && typeof profile === 'object' && profile.name) {
                            mobileProfile = profile;
                            break;
                        }
                    } catch (_) {}
                }
            } catch (_) {}
        }

        if (mobileProfile) {
            let backendPairConfirmed = false;
            if (syncClient?.token && syncClient?.deviceId) {
                try {
                    await syncClient.pairDevice(code, syncClient.deviceId);
                    backendPairConfirmed = true;
                } catch(error) {
                    console.warn('Backend pairing failed, continuing with mobile-code mode:', error.message || error);
                }
            }

            state.userProfile = normalizeMobileProfile(mobileProfile);
            state.userName = state.userProfile.name || state.userName;
            state.pairedDeviceName = state.userProfile.deviceName || state.userProfile.device || 'Android';
            state.isPaired = true;
            state.syncMode = backendPairConfirmed ? 'backend' : 'mobile-code';
            saveState();

            // Push ACK back to ntfy.sh so Android detects pairing — use PowerShell to bypass CORS
            try {
                if (isTauri()) {
                    await invokeTauri('run_shell_command', {
                        cmd: 'powershell.exe',
                        args: ['-NoProfile', '-Command', `Invoke-WebRequest -Uri "https://ntfy.sh/danbron_sync_${code}_ack" -Method POST -Body '{"paired":true,"device":"Windows PC","ts":${Date.now()}}' -ContentType 'application/json' -TimeoutSec 10 | Out-Null`]
                    });
                } else {
                    await fetch(`https://ntfy.sh/danbron_sync_${code}_ack`, {
                        method: 'POST',
                        body: JSON.stringify({ paired: true, device: 'Windows PC', ts: Date.now() })
                    });
                }
            } catch(_) {}

            const $modal = $('pairingModal');
            if ($modal) $modal.remove();

            showChat();
            showNotification(`Vinculado con ${state.userName}. Ya tengo tus datos.`);
            return;
        }

        // Try 3: Backend pairing (needs auth)
        if (syncClient?.token && syncClient?.deviceId) {
            try {
                await syncClient.pairDevice(code, syncClient.deviceId);
                
                const $modal = $('pairingModal');
                if ($modal) $modal.remove();
                
                state.isPaired = true;
                saveState();
                
                showChat();
                showNotification('Telefono vinculado.');
                return;
            } catch(_) {}
        }

        showNotification('No encontre datos con ese codigo. Asegurate de tocar "Vincular con mi PC" en tu telefono primero, espera 5 segundos, y luego escribe el codigo aqui.');
    } catch (error) {
        console.error('Pairing error:', error);
        showNotification('No pude vincular. Genera un codigo nuevo desde tu telefono e intenta otra vez.');
    } finally {
        if (confirmBtn) { confirmBtn.textContent = 'Listo'; confirmBtn.disabled = false; }
    }
}

function copyPairingCode() {
    const code = $('pcPairingCode').textContent;
    if (code && code !== 'Generando...' && code !== 'Error' && code !== 'Offline' && !code.includes('telefono')) {
        navigator.clipboard.writeText(code);
        showNotification('Codigo copiado.');
    } else {
        showNotification('Usa el codigo de 6 digitos que aparece en tu telefono (seccion Yo > Vincular con PC).');
    }
}

async function fetchMobileProfileByCode(code) {
    try {
        const response = await fetch(`https://ntfy.sh/danbron_sync_${code}/json?poll=1&since=all`, {
            cache: 'no-store'
        });

        if (!response.ok) return null;

        const text = await response.text();
        const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean).reverse();

        for (const line of lines) {
            try {
                const event = JSON.parse(line);
                const raw = event.message || event.content || event.data;
                if (!raw) continue;

                const profile = typeof raw === 'string' ? JSON.parse(raw) : raw;
                if (profile && typeof profile === 'object' && profile.name) {
                    return profile;
                }
            } catch (_) {}
        }
    } catch (error) {
        console.warn('Mobile code sync failed:', error);
    }

    return null;
}

function normalizeMobileProfile(profile) {
    return {
        ...profile,
        income: Number(profile.income || 0),
        expenses: Number(profile.expenses || 0),
        debt: Number(profile.debt || 0)
    };
}

function setSetupStatus(message, isError = false) {
    const el = $('setupStatus');
    if (!el) return;
    el.textContent = message || '';
    el.classList.toggle('active', !!message);
    el.classList.toggle('error', isError);
}

function skipPairing() {
    const $modal = $('pairingModal');
    if ($modal) $modal.remove();
    showDashboard();
}

window.nativeMinimize = nativeMinimize;
window.nativeHide = nativeHide;
window.togglePin = togglePin;
window.showDashboard = showDashboard;
window.showChat = showChat;
window.showContext = showContext;
window.showProfile = showProfile;
window.showProgress = showProgress;
window.showNotes = showNotes;
window.refreshPcContext = refreshPcContext;
window.setPrompt = setPrompt;
window.setPromptAndOpen = setPromptAndOpen;
window.openSettings = openSettings;
window.addDesktopNote = addDesktopNote;
window.saveProfile = saveProfile;
window.deleteDesktopNote = deleteDesktopNote;
window.confirmPairingCode = confirmPairingCode;
window.copyPairingCode = copyPairingCode;
window.skipPairing = skipPairing;

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
    stopCommandPolling();
    stopSharedStatePolling();
    
    saveState();
    showSetup();
}

async function handleSend() {
    const text = $('chatInput').value.trim();
    if (!text || state.isTyping) return;

    state.messages.push({ role: 'user', content: text, time: formatTime(), timestamp: Date.now() });
    $('chatInput').value = '';
    renderMessages();
    scrollToBottom();

    state.isTyping = true;
    showTyping();

    try {
        updateOrchestratorBridge();
        const useV3Orchestrator = /\b(prueba completa|prueba integral|agent test|todas las skills|varias skills)\b/i.test(normalizeText(text))
            && window.BronOrchestrator;
        const localCommand = useV3Orchestrator ? null : parseLocalDanbronCommand(text);
        const reply = useV3Orchestrator
            ? await window.BronOrchestrator.run(text, state)
            : localCommand
            ? await executeLocalDanbronCommand(localCommand)
            : await callAI(text);
        state.messages.push({ role: 'assistant', content: reply, time: formatTime(), timestamp: Date.now() });
    } catch (err) {
        // Show error as notification instead of in message history
        showNotification('No pude responder ahora. Intenta de nuevo.');
        // Remove the user message since we failed to respond
        state.messages.pop();
    }

    state.isTyping = false;
    hideTyping();
    saveState();
    scheduleSharedSync('chat-message');
    renderMessages();
    scrollToBottom();
}

function normalizeText(text) {
    return text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

function parseLocalDanbronCommand(text) {
    const t = normalizeText(text);
    const wantsMutation = /\b(agrega|agregame|anade|crea|guarda|edita|actualiza|cambia|marca|registrame)\b/.test(t);
    if (!wantsMutation) return null;

    if (/\b(nota|notas|anota|recuerda)\b/.test(t)) {
        const body = cleanCommandPayload(extractAfterAny(text, ['nota', 'anota', 'recuerda que', 'recuerda', 'guarda']) || text);
        const content = body || text;
        return {
            action: 'add_note',
            args: {
                title: content.replace(/\s+/g, ' ').slice(0, 54) || 'Nota rapida',
                content,
                tag: /\b(finanza|dinero|plata|deuda|ingreso|gasto)\b/.test(t) ? 'finance' : /\b(meta|objetivo|progreso)\b/.test(t) ? 'goal' : 'general'
            }
        };
    }

    if (/\b(perfil|ingreso|ingresos|gasto|gastos|deuda|trabajo|chofer|uber|driver)\b/.test(t)) {
        const patch = {};
        const income = extractNumberNear(t, ['ingreso', 'ingresos', 'gano', 'ganancia']);
        const expenses = extractNumberNear(t, ['gasto', 'gastos', 'costo', 'costos']);
        const debt = extractNumberNear(t, ['deuda', 'debo']);
        if (income != null) patch.income = income;
        if (expenses != null) patch.expenses = expenses;
        if (debt != null) patch.debt = debt;
        if (/\b(uber|chofer|conductor|driver)\b/.test(t)) {
            patch.workStyle = 'driver';
            patch.lifeSummary = `${state.userProfile?.lifeSummary || ''} Trabaja como conductor/chofer; Danbron debe ayudar con ganancias, horas, viajes, combustible y plan financiero.`.trim();
        }
        return Object.keys(patch).length ? { action: 'update_profile', args: patch } : null;
    }

    if (/\b(habito|habitos|racha|progreso)\b/.test(t)) {
        const isMark = /\b(marca|complete|completa|hecho|cumpli)\b/.test(t);
        const body = cleanCommandPayload(extractAfterAny(text, ['habito', 'progreso', 'racha', 'marca', 'agrega', 'agregame', 'anade']) || text);
        return {
            action: isMark ? 'mark_habit' : 'add_habit',
            args: {
                name: body || 'Progreso personal'
            }
        };
    }

    return null;
}

async function executeLocalDanbronCommand(command) {
    if (command.action === 'add_note') {
        const now = Date.now();
        state.notes = [
            { id: now, ...command.args, createdAt: now, updatedAt: now },
            ...(state.notes || [])
        ];
        saveState();
        scheduleSharedSync('chat-note-added');
        renderCommandCenter();
        return `Listo. Agregue la nota real en Danbron Desktop: "${command.args.title}".`;
    }

    if (command.action === 'add_habit') {
        const now = Date.now();
        const habit = {
            id: now,
            name: String(command.args.name || 'Progreso personal').replace(/^\w/, c => c.toUpperCase()),
            streak: 0,
            week: [false, false, false, false, false, false, false],
            updatedAt: now
        };
        state.habits = [habit, ...(state.habits || [])];
        saveState();
        scheduleSharedSync('chat-habit-added');
        renderCommandCenter();
        return `Listo. Agregue el progreso real en Danbron Desktop: "${habit.name}".`;
    }

    if (command.action === 'mark_habit') {
        const query = normalizeText(command.args.name || '');
        const habit = (state.habits || []).find(h => normalizeText(h.name || '').includes(query) || query.includes(normalizeText(h.name || '')))
            || (state.habits || [])[0];
        if (!habit) return `No encontre un progreso parecido a "${command.args.name}".`;
        const today = new Date().toISOString().slice(0, 10);
        const exists = (state.habitLogs || []).some(log => Number(log.habitId) === Number(habit.id) && log.date === today);
        if (!exists) {
            state.habitLogs = [{ habitId: habit.id, date: today, completed: true, updatedAt: Date.now() }, ...(state.habitLogs || [])];
            state.habits = (state.habits || []).map(h => Number(h.id) === Number(habit.id) ? { ...h, streak: Number(h.streak || 0) + 1, updatedAt: Date.now() } : h);
        }
        saveState();
        scheduleSharedSync('chat-habit-marked');
        renderCommandCenter();
        return `Listo. Marque progreso de hoy para "${habit.name}".`;
    }

    if (command.action === 'update_profile') {
        state.userProfile = {
            ...(state.userProfile || {}),
            ...command.args,
            _syncUpdatedAt: Date.now()
        };
        saveState();
        scheduleSharedSync('chat-profile-updated');
        renderCommandCenter();
        return `Listo. Actualice el perfil real en Danbron Desktop.`;
    }

    return 'No reconozco esa accion interna de Danbron Desktop.';
}

function extractNumberNear(text, markers) {
    for (const marker of markers) {
        const match = new RegExp(`${marker}\\D{0,18}([0-9][0-9.,]*)`, 'i').exec(text);
        if (match?.[1]) {
            const n = Number(String(match[1]).replace(/\./g, '').replace(',', '.'));
            if (Number.isFinite(n)) return n;
        }
    }
    return null;
}

function parseAutomationCommand(text) {
    const t = normalizeText(text);
    const looksLikeCommand = /\b(abre|abrir|pon|reproduce|escucha|busca|buscar|envia|enviar|manda|mandar|escribe|redacta|entra|agenda|agendar|crea|crear|programa|navega|lleva|briefing|resumen del dia|que tengo hoy|que necesito saber|clima|tiempo|weather|temperatura|timer|temporizador|alarma|recuerdame|recordatorio|calcula|cuanto es|calculadora|traduce|traducir|translate|como se dice|limpiar|cleanup|optimizar|liberar espacio|screenshot|captura|que hay en pantalla|instagram|tiktok|twitter|facebook|spotify|musica|pausa|siguiente cancion|volumen|archivo|carpeta|descargas|google|precio de|cuanto cuesta|noticias)\b/.test(t);
    const looksLikeAgentTest = /\b(prueba completa|prueba integral|modo agente|agent test|todas las skills|varias skills)\b/.test(t);
    if (!looksLikeCommand && !looksLikeAgentTest) return null;

    if (looksLikeAgentTest) {
        return {
            target: 'local',
            action: 'agent_test',
            args: { prompt: text }
        };
    }

    const explicitAndroidTarget = /\b(?:en|desde|al|a|mi)\s+(?:telefono|celular|android|movil)\b/.test(t);
    const target = explicitAndroidTarget
        ? 'android'
        : /\b(pc|computador|ordenador|windows)\b/.test(t)
        ? 'windows'
        : 'windows';

    const skillCommand = parseSkillCommand(text, target);
    if (skillCommand) return skillCommand;

    if (/\b(gmail|correo|email|mail)\b/.test(t) && /\b(envia|enviar|manda|mandar|escribe|redacta|correo|email|mail)\b/.test(t)) {
        const action = /\b(envia|enviar|envialo|manda|mandar|mandalo)\b/.test(t)
            ? 'send_email'
            : 'compose_email';
        return {
            target: explicitAndroidTarget ? 'android' : 'windows',
            action,
            args: extractEmailDraft(text),
            requiresConfirmation: action !== 'send_email'
        };
    }

    if (/\b(calendario|calendar|agenda|agendar|evento|reunion|cita)\b/.test(t)) {
        return {
            target,
            action: 'create_calendar_event',
            args: extractCalendarEvent(text),
            requiresConfirmation: true
        };
    }

    if (/\b(musica|cancion|spotify|youtube music|yt music|reproduce|escucha|pon)\b/.test(t)) {
        const query = cleanCommandPayload(extractAfterAny(text, ['spotify', 'youtube music', 'yt music', 'musica', 'cancion', 'reproduce', 'escucha', 'pon']) || text);
        const service = /\bspotify\b/.test(t) ? 'spotify' : /\b(youtube music|yt music)\b/.test(t) ? 'youtube_music' : 'auto';
        return {
            target,
            action: 'play_music',
            args: { query: query.trim(), service }
        };
    }

    if (/\b(mapa|maps|direccion|ruta|navega|lleva|ubicacion)\b/.test(t)) {
        const query = cleanCommandPayload(extractAfterAny(text, ['maps', 'mapa', 'direccion', 'ruta', 'navega', 'lleva', 'ubicacion']) || text);
        return {
            target,
            action: 'open_maps',
            args: { query: query.trim() }
        };
    }

    const appMap = [
        ['whatsapp', 'whatsapp'],
        ['wsp', 'whatsapp'],
        ['opera', 'opera'],
        ['chrome', 'chrome'],
        ['gmail', 'gmail'],
        ['correo', 'gmail'],
        ['calendar', 'calendar'],
        ['calendario', 'calendar'],
        ['spotify', 'spotify'],
        ['musica', 'music'],
        ['youtube', 'youtube'],
        ['portal', 'portal_universidad'],
        ['universidad', 'portal_universidad']
    ];
    const appEntry = appMap.find(([term]) => t.includes(term));
    const app = appEntry?.[1] || null;

    if (app === 'whatsapp' && /\b(envia|enviar|manda|mandar|escribe|redacta)\b/.test(t)) {
        const message = extractMessagePayload(text);
        return {
            target,
            action: 'compose_whatsapp',
            args: { message },
            requiresConfirmation: true
        };
    }

    if (app === 'youtube') {
        const query = extractYouTubeQuery(text);
        return {
            target,
            action: query.trim() ? 'open_url' : 'open_app',
            args: query.trim()
                ? { url: `https://www.youtube.com/results?search_query=${encodeURIComponent(query.trim())}` }
                : { app: 'youtube' }
        };
    }

    if (app === 'portal_universidad') {
        return {
            target,
            action: 'open_url',
            args: { url: 'https://portal.uv.cl/' }
        };
    }

    if (app) {
        return { target, action: 'open_app', args: { app } };
    }

    if (/\b(busca|buscar)\b/.test(t)) {
        const query = cleanCommandPayload(extractAfterAny(text, ['busca', 'buscar']) || text);
        return {
            target,
            action: 'open_url',
            args: { url: `https://www.google.com/search?q=${encodeURIComponent(query.trim())}` }
        };
    }

    return null;
}

function cleanCommandPayload(value) {
    return String(value || '')
        .replace(/^\s*(con|de|sobre|acerca de|en|mi|el|la|por|para)\s+/i, '')
        .split(/\s*(?:,|\.)?\s+y\s+(?:despues|luego|entonces)\b/i)[0]
        .split(/\s*(?:,|\.)?\s+and\s+(?:then|after)\b/i)[0]
        .replace(/\s+/g, ' ')
        .trim();
}

function extractYouTubeQuery(text) {
    const afterYoutube = extractAfterAny(text, ['youtube']);
    const direct = cleanCommandPayload(afterYoutube);
    if (direct && !/^(abre|abrir|busca|buscar|pon)$/i.test(direct)) return direct;

    const afterIntent = extractAfterAny(text, ['busca', 'buscar', 'pon', 'reproduce', 'escucha']);
    const intentQuery = cleanCommandPayload(afterIntent);
    if (intentQuery) return intentQuery;

    return '';
}

function extractAfterAny(text, markers) {
    const normalized = normalizeText(text);
    for (const marker of markers) {
        const idx = normalized.indexOf(marker);
        if (idx >= 0) return text.slice(idx + marker.length).replace(/^(en|mi|el|la|por|para)\s+/i, '').trim();
    }
    return '';
}

function extractMessagePayload(text) {
    const quoted = text.match(/["â€œ](.+?)["â€]/);
    if (quoted?.[1]) return quoted[1].trim();
    return extractAfterAny(text, ['envia', 'enviar', 'manda', 'mandar', 'escribe', 'redacta']).trim();
}

function parseSkillCommand(text, target) {
    if (typeof DanbronSkillRegistry === 'undefined') return null;
    const skill = DanbronSkillRegistry.findSkillForText(text);
    if (!skill) return null;

    const t = normalizeText(text);

    if (skill.id === 'ai-daily-briefing') {
        return {
            target: 'local',
            skill: 'ai-daily-briefing',
            action: 'daily_briefing',
            args: {}
        };
    }

    // Weather skill
    if (skill.id === 'weather') {
        const city = extractAfterAny(text, ['clima', 'tiempo', 'weather', 'temperatura', 'pronostico']) || '';
        return { target: 'local', skill: 'weather', action: 'check_weather', args: { query: city.trim() || 'mi ubicacion' } };
    }

    // Timer & Reminders
    if (skill.id === 'timer-reminders') {
        const minutes = text.match(/(\d+)\s*min/i)?.[1] || text.match(/(\d+)\s*hora/i)?.[1];
        const reminderText = extractAfterAny(text, ['recuerdame', 'recordatorio', 'remind', 'alarma', 'timer']);
        if (/\b(timer|temporizador|alarma)\b/.test(t)) {
            return { target: 'windows', skill: 'timer-reminders', action: 'set_timer', args: { minutes: parseInt(minutes) || 5, label: reminderText || 'Timer' } };
        }
        return { target: 'windows', skill: 'timer-reminders', action: 'set_reminder', args: { text: reminderText || text, minutes: parseInt(minutes) || 10 } };
    }

    // Web Search
    if (skill.id === 'web-search') {
        const query = extractAfterAny(text, ['busca', 'buscar', 'google', 'search', 'investiga', 'precio de', 'cuanto cuesta', 'que es']) || text;
        return { target: 'windows', skill: 'web-search', action: 'web_search', args: { query: query.trim() } };
    }

    // File Manager
    if (skill.id === 'file-manager') {
        const target2 = extractAfterAny(text, ['archivo', 'carpeta', 'busca archivo', 'abrir archivo', 'file', 'folder']);
        if (/\b(abrir|abre|open)\b/.test(t)) {
            return { target: 'windows', skill: 'file-manager', action: 'open_file', args: { path: target2 || 'descargas' } };
        }
        return { target: 'windows', skill: 'file-manager', action: 'find_file', args: { query: target2 || text } };
    }

    // Music & Media
    if (skill.id === 'music-media') {
        const query = extractAfterAny(text, ['pon', 'reproduce', 'escucha', 'play', 'musica', 'music', 'cancion']);
        const isSpotify = /spotify/i.test(t);
        if (/\b(pausa|pause|stop|para)\b/.test(t)) {
            return { target: 'windows', skill: 'music-media', action: 'media_control', args: { action: 'pause' } };
        }
        if (/\b(siguiente|next|skip)\b/.test(t)) {
            return { target: 'windows', skill: 'music-media', action: 'media_control', args: { action: 'next' } };
        }
        if (/\b(volumen|volume)\b/.test(t)) {
            const level = text.match(/(\d+)/)?.[1] || '50';
            return { target: 'windows', skill: 'music-media', action: 'media_control', args: { action: 'volume', level } };
        }
        return { target: 'windows', skill: 'music-media', action: 'play_music', args: { query: query || '', service: isSpotify ? 'spotify' : 'youtube' } };
    }

    // Calculator
    if (skill.id === 'calculator') {
        const expr = extractAfterAny(text, ['calcula', 'cuanto es', 'calculadora']) || text;
        return { target: 'local', skill: 'calculator', action: 'calculate', args: { expression: expr.trim() } };
    }

    // Translator
    if (skill.id === 'translator') {
        const textToTranslate = extractAfterAny(text, ['traduce', 'traducir', 'translate', 'como se dice']) || text;
        const toLang = /\b(ingles|english)\b/.test(t) ? 'en' : /\b(espanol|spanish)\b/.test(t) ? 'es' : 'en';
        return { target: 'local', skill: 'translator', action: 'translate_text', args: { text: textToTranslate, to: toLang } };
    }

    // System Cleanup
    if (skill.id === 'system-cleanup') {
        if (/\b(limpiar|cleanup|temp|cache)\b/.test(t)) {
            return { target: 'windows', skill: 'system-cleanup', action: 'cleanup_temp', args: {} };
        }
        if (/\b(espacio|disco|disk)\b/.test(t)) {
            return { target: 'windows', skill: 'system-cleanup', action: 'disk_analysis', args: {} };
        }
        return { target: 'windows', skill: 'system-cleanup', action: 'optimize', args: {} };
    }

    // Screenshot & OCR
    if (skill.id === 'screenshot-ocr') {
        return { target: 'windows', skill: 'screenshot-ocr', action: 'take_screenshot', args: {} };
    }

    // Social Media
    if (skill.id === 'social-media') {
        const platform = /instagram/i.test(t) ? 'instagram' : /tiktok/i.test(t) ? 'tiktok' : /twitter/i.test(t) ? 'twitter' : /facebook/i.test(t) ? 'facebook' : 'instagram';
        return { target: 'windows', skill: 'social-media', action: 'open_url', args: { url: `https://www.${platform}.com/`, app: platform } };
    }

    if (skill.id !== 'gog' && skill.id !== 'gmail') {
        return null;
    }

    if (/\b(gmail|correo|email|mail)\b/.test(t) && /\b(envia|enviar|manda|mandar|escribe|redacta)\b/.test(t)) {
        const explicitAndroidTarget = /\b(?:en|desde|al|a|mi)\s+(?:telefono|celular|android|movil)\b/.test(t);
        const action = /\b(envia|enviar|envialo|manda|mandar|mandalo)\b/.test(t)
            ? 'send_email'
            : 'compose_email';
        return {
            target: explicitAndroidTarget ? 'android' : 'windows',
            skill: 'gog',
            action,
            args: extractEmailDraft(text),
            requiresConfirmation: action !== 'send_email'
        };
    }

    if (/\b(calendario|calendar|agenda|agendar|evento|reunion|cita)\b/.test(t)) {
        return {
            target,
            skill: 'gog',
            action: 'create_calendar_event',
            args: extractCalendarEvent(text),
            requiresConfirmation: true
        };
    }

    if (/\b(mapa|maps|direccion|ruta|navega|lleva|ubicacion)\b/.test(t)) {
        return {
            target,
            skill: 'gog',
            action: 'open_maps',
            args: { query: extractAfterAny(text, ['maps', 'mapa', 'direccion', 'ruta', 'navega', 'lleva', 'ubicacion']) || text }
        };
    }

    const workspaceApp = detectWorkspaceApp(t);
    if (workspaceApp) {
        return {
            target,
            skill: 'gog',
            action: 'open_app',
            args: { app: workspaceApp }
        };
    }

    return null;
}

function detectWorkspaceApp(t) {
    if (/\b(gmail|correo|email|mail)\b/.test(t)) return 'gmail';
    if (/\b(calendar|calendario|agenda)\b/.test(t)) return 'calendar';
    if (/\bdrive\b/.test(t)) return 'drive';
    if (/\b(docs|documento|documentos)\b/.test(t)) return 'docs';
    if (/\b(sheets|hoja|planilla|calculo)\b/.test(t)) return 'sheets';
    if (/\b(contactos|contacts)\b/.test(t)) return 'contacts';
    if (/\binstagram\b/.test(t)) return 'instagram';
    if (/\btiktok\b/.test(t)) return 'tiktok';
    if (/\b(twitter|x\.com)\b/.test(t)) return 'twitter';
    if (/\bfacebook\b/.test(t)) return 'facebook';
    if (/\btelegram\b/.test(t)) return 'telegram';
    if (/\bdiscord\b/.test(t)) return 'discord';
    if (/\bnotion\b/.test(t)) return 'notion';
    if (/\btrello\b/.test(t)) return 'trello';
    if (/\bchatgpt\b/.test(t)) return 'chatgpt';
    if (/\bclaude\b/.test(t)) return 'claude';
    if (/\bnetflix\b/.test(t)) return 'netflix';
    if (/\bamazon\b/.test(t)) return 'amazon';
    if (/\blinkedin\b/.test(t)) return 'linkedin';
    if (/\breddit\b/.test(t)) return 'reddit';
    if (/\btwitch\b/.test(t)) return 'twitch';
    if (/\bgithub\b/.test(t)) return 'github';
    if (/\bfigma\b/.test(t)) return 'figma';
    if (/\bcanva\b/.test(t)) return 'canva';
    if (/\bspotify\b/.test(t)) return 'spotify';
    if (/\byoutube\b/.test(t)) return 'youtube';
    if (/\bwhatsapp\b/.test(t)) return 'whatsapp';
    if (/\bopera\b/.test(t)) return 'opera';
    if (/\bchrome\b/.test(t)) return 'chrome';
    if (/\b(paint|dibujar)\b/.test(t)) return 'paint';
    if (/\b(terminal|consola|cmd|powershell)\b/.test(t)) return 'terminal';
    if (/\b(explorador|explorer)\b/.test(t)) return 'explorer';
    if (/\b(bloc de notas|notepad)\b/.test(t)) return 'notepad';
    return null;
}

function extractEmailDraft(text) {
    const toMatch = text.match(/\b(?:a|para)\s+([^\s,;]+@[^\s,;]+)/i);
    const subjectMatch = text.match(/\b(?:asunto|subject)\s*[:\-]?\s*(?:"|\u201c)?(.+?)(?:"|\u201d|\s+(?:diciendo que|que diga|con cuerpo|cuerpo|mensaje)\b|[.]\s|$)/i);
    const body = extractEmailBody(text) || extractMessagePayload(text) || extractAfterAny(text, ['correo', 'email', 'mail', 'gmail']) || text;
    return {
        to: toMatch?.[1] || '',
        subject: cleanEmailField(subjectMatch?.[1] || ''),
        body: cleanEmailField(body)
    };
}

function extractEmailBody(text) {
    const markers = [
        /(?:diciendo que|que diga que|cuerpo|mensaje)\s*[:\-]?\s*(.+)$/i,
        /(?:body)\s*[:\-]?\s*(.+)$/i
    ];
    for (const marker of markers) {
        const match = text.match(marker);
        if (match?.[1]) return stripEmailInstructions(match[1]);
    }
    return '';
}

function stripEmailInstructions(value) {
    return String(value || '')
        .replace(/\b(escribelo|escríbelo|redactalo|redáctalo)\b[\s\S]*$/i, '')
        .replace(/\b(y\s+)?(envialo|envíalo|envia|envía|mandalo|mándalo|manda)\s+(ahora|desde gmail|por gmail|ya)?[\s\S]*$/i, '')
        .trim();
}

function cleanEmailField(value) {
    return String(value || '')
        .replace(/^["\u201c]+|["\u201d.]+$/g, '')
        .replace(/\s+/g, ' ')
        .trim();
}

function extractCalendarEvent(text) {
    const quoted = text.match(/["â€œ](.+?)["â€]/);
    const title = quoted?.[1]?.trim()
        || extractAfterAny(text, ['agenda', 'agendar', 'crea', 'crear', 'programa', 'evento', 'reunion', 'cita'])
        || 'Nuevo evento';
    return {
        title: title.trim(),
        details: text.trim()
    };
}

async function generateDailyBriefing() {
    await refreshPcContext(false);
    const gmail = await runGogText(['gmail', 'search', 'is:unread', '--max', '5']);
    const googleCalendar = await runGogText(['calendar', 'events', 'primary']);
    const drive = await runGogText(['drive', 'ls', '--max', '5']);
    const now = new Date();
    const day = now.toLocaleDateString('es', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
    const notes = (state.notes || []).slice(0, 5);
    const ctx = state.systemContext || {};
    const windows = (ctx.activeWindows || []).slice(0, 5);
    const comms = (ctx.comms || []).slice(0, 5);
    const profile = state.userProfile || {};

    const priorities = [];
    if (profile.debt && profile.income) {
        priorities.push(`Revisar plan financiero: deuda $${Number(profile.debt).toLocaleString()} con ingresos $${Number(profile.income).toLocaleString()}.`);
    }
    notes.forEach(note => priorities.push(`${note.title || 'Nota'} - ${String(note.content || '').slice(0, 90)}`));
    if (windows.length > 0) {
        priorities.push(`Aprovechar contexto actual del PC: ${windows.map(w => w.processName).join(', ')}.`);
    }
    if (gmail.ok && gmail.text.trim()) {
        priorities.push('Revisar los correos no leidos mas importantes.');
    }

    const gmailBlock = gmail.ok && gmail.text.trim()
        ? gmail.text.trim()
        : 'No pude leer Gmail desde Gog en este momento.';

    const calendar = googleCalendar.ok && googleCalendar.text.trim()
        ? googleCalendar.text.trim()
        : comms.length
        ? comms.map(item => `- ${item.source}: ${item.title}`).join('\n')
        : 'Sin eventos visibles en Google Calendar.';

    const driveBlock = drive.ok && drive.text.trim()
        ? drive.text.trim()
        : 'Drive sin resultados o no disponible en este momento.';

    const context = windows.length
        ? windows.map(w => `- ${w.processName}: ${w.windowTitle}`).join('\n')
        : 'No tengo contexto visible del PC actualizado.';

    const priorityBlock = priorities.length
        ? priorities.slice(0, 5).map((item, idx) => `${idx + 1}. [ ] ${item}`).join('\n')
        : '1. [ ] Revisa Gmail.\n2. [ ] Revisa calendario.\n3. [ ] Elige una accion concreta para avanzar ahora.';

    const focus = priorities[0] || 'Revisar tus fuentes conectadas y elegir una prioridad concreta.';

    return [
        `DAILY BRIEFING - ${day}`,
        '',
        "TODAY'S PRIORITIES",
        priorityBlock,
        '',
        'GMAIL',
        gmailBlock,
        '',
        'CALENDAR / SIGNALS',
        calendar,
        '',
        'DRIVE',
        driveBlock,
        '',
        'CONTEXT',
        context,
        '',
        'FOCUS FOR TODAY',
        focus
    ].join('\n');
}

async function runGogText(args) {
    if (!window.__TAURI__) return { ok: false, text: '' };

    try {
        const command = ['gog', ...args.map(psQuote)].join(' ');
        const output = await invokeTauri('run_shell_command', {
            cmd: 'powershell.exe',
            args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command]
        });
        return { ok: true, text: String(output || '') };
    } catch (error) {
        console.debug('gog command failed:', args, error);
        return { ok: false, text: error?.message || String(error || '') };
    }
}

function localRfc3339(date) {
    const pad = value => String(value).padStart(2, '0');
    const offsetMinutes = -date.getTimezoneOffset();
    const sign = offsetMinutes >= 0 ? '+' : '-';
    const abs = Math.abs(offsetMinutes);
    return [
        date.getFullYear(),
        '-',
        pad(date.getMonth() + 1),
        '-',
        pad(date.getDate()),
        'T',
        pad(date.getHours()),
        ':',
        pad(date.getMinutes()),
        ':00',
        sign,
        pad(Math.floor(abs / 60)),
        ':',
        pad(abs % 60)
    ].join('');
}

function clipBlock(text, max = 1200) {
    const clean = String(text || '').trim();
    if (!clean) return '';
    return clean.length > max ? clean.slice(0, max).trimEnd() + '\n...' : clean;
}

async function runIntegratedAgentTest(promptText) {
    const results = [];
    const mark = (name, ok, detail) => {
        results.push({ name, ok, detail: clipBlock(detail, 1400) });
    };

    await refreshPcContext(false);
    const ctx = state.systemContext || {};
    const windows = (ctx.activeWindows || []).slice(0, 8).map(w => `${w.processName}: ${w.windowTitle}`);
    const comms = (ctx.comms || []).slice(0, 6).map(c => `${c.source}: ${c.title || c.processName}`);
    const games = (ctx.games || []).filter(g => g.running).slice(0, 6).map(g => g.name);
    mark(
        'Contexto Windows',
        windows.length > 0,
        [
            windows.length ? windows.join('\n') : 'No hubo ventanas visibles.',
            comms.length ? `Comunicacion: ${comms.join(' | ')}` : 'Sin comunicacion visible.',
            games.length ? `Distracciones/video/juegos: ${games.join(' | ')}` : 'Sin distracciones visibles.'
        ].join('\n')
    );

    const gmail = await runGogText(['gmail', 'search', 'is:unread', '--max', '5']);
    mark('Gmail no leidos', gmail.ok, gmail.ok ? (gmail.text || 'Sin correos no leidos.') : gmail.text);

    const calendar = await runGogText(['calendar', 'events', 'primary', '--today', '--max', '10']);
    mark('Calendar hoy', calendar.ok, calendar.ok ? (calendar.text || 'Sin eventos hoy.') : calendar.text);

    const drive = await runGogText(['drive', 'ls', '--max', '5']);
    mark('Drive', drive.ok, drive.ok ? (drive.text || 'Drive sin archivos visibles.') : drive.text);

    const start = new Date();
    start.setDate(start.getDate() + 1);
    start.setHours(10, 0, 0, 0);
    const end = new Date(start.getTime() + 30 * 60 * 1000);
    const createEvent = await runGogText([
        'calendar',
        'create',
        'primary',
        '--summary',
        'Revision Danbron Agent Test',
        '--from',
        localRfc3339(start),
        '--to',
        localRfc3339(end),
        '--start-timezone',
        'America/Santiago',
        '--end-timezone',
        'America/Santiago',
        '--description',
        'Prueba integral de Gmail, Calendar, Drive, contexto Windows y automatizacion.',
        '--force',
        '--no-input'
    ]);
    mark('Crear evento Calendar', createEvent.ok, createEvent.ok ? (createEvent.text || 'Evento creado.') : createEvent.text);

    const emailBody = [
        'Hola Brandon,',
        '',
        'Danbron ejecuto una prueba integral: contexto de Windows, Gmail, Calendar, Drive, creacion de evento, envio de correo y automatizacion Android-PC.',
        '',
        'Este correo confirma que el flujo de agente funciona desde Windows usando Gog y OpenRouter.',
        '',
        'Saludos,',
        'Danbron'
    ].join('\n');
    const sendEmail = await runGogText([
        'gmail',
        'send',
        '--to',
        'brandontorres.dev@gmail.com',
        '--subject',
        'Danbron Agent Test completado',
        '--body',
        emailBody,
        '--force',
        '--no-input'
    ]);
    mark('Enviar correo Gmail', sendEmail.ok, sendEmail.ok ? (sendEmail.text || 'Correo enviado.') : sendEmail.text);

    let phoneDetail = 'Telefono no conectado.';
    let phoneOk = false;
    if (syncClient && syncClient.isAuthenticated() && syncClient.isPaired()) {
        try {
            const queued = await syncClient.sendRemoteCommand({
                target: 'android',
                action: 'open_app',
                args: { app: 'youtube' },
                requiresConfirmation: false
            });
            phoneOk = !!queued?.id;
            phoneDetail = phoneOk ? `Orden enviada al telefono. ID: ${queued.id}` : 'El servidor no devolvio ID.';
        } catch (error) {
            phoneDetail = error.message || String(error);
        }
    }
    mark('Android-PC sync', phoneOk, phoneDetail);

    const checklist = results
        .map(item => `${item.ok ? '[OK]' : '[FALLO]'} ${item.name}\n${item.detail || 'Sin detalle.'}`)
        .join('\n\n');
    const failed = results.filter(item => !item.ok).map(item => item.name);

    return [
        'DANBRON AGENT TEST',
        '',
        checklist,
        '',
        'RESUMEN',
        failed.length
            ? `Funciono parcialmente. Fallaron: ${failed.join(', ')}.`
            : 'Funciono completo: contexto Windows, Gmail, Calendar, Drive, evento, correo y Android-PC sync.',
        '',
        'NOTA',
        'No invente resultados: cada bloque viene de una herramienta real o reporta su fallo.'
    ].join('\n');
}

async function executeAutomationCommand(command) {
    if (command.target === 'local' && command.action === 'daily_briefing') {
        return await generateDailyBriefing();
    }

    if (command.target === 'local' && command.action === 'agent_test') {
        return await runIntegratedAgentTest(command.args?.prompt || '');
    }

    // Weather — use AI to describe weather (no API key needed)
    if (command.action === 'check_weather') {
        const q = command.args?.query || 'mi ciudad';
        try {
            const result = await invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-Command', `(Invoke-WebRequest -Uri "https://wttr.in/${encodeURIComponent(q)}?format=%C+%t+%h+%w&lang=es" -TimeoutSec 5).Content`]
            });
            return `🌤️ Clima en ${q}: ${result.trim()}`;
        } catch(e) {
            return `No pude consultar el clima. Intenta con "busca clima en ${q}" para abrir el navegador.`;
        }
    }

    // Calculator — eval safe math
    if (command.action === 'calculate') {
        const expr = command.args?.expression || '';
        try {
            const sanitized = expr.replace(/[^0-9+\-*/().,%\s]/g, '').replace(/,/g, '.');
            const result = await invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-Command', `[math]::Round((${sanitized}), 4)`]
            });
            return `🧮 ${expr} = **${result.trim()}**`;
        } catch(e) {
            return `No pude calcular "${expr}". Revisa la expresion.`;
        }
    }

    // Translator — use AI
    if (command.action === 'translate_text') {
        const txt = command.args?.text || '';
        const toLang = command.args?.to === 'es' ? 'español' : 'inglés';
        try {
            const reply = await rawAICall([
                { role: 'system', content: `Eres un traductor. Traduce al ${toLang}. Solo responde con la traduccion, nada mas.` },
                { role: 'user', content: txt }
            ]);
            return `🌐 **Traduccion al ${toLang}:**\n${reply}`;
        } catch(e) {
            return `No pude traducir. Error: ${e.message}`;
        }
    }

    // Timer / Reminder
    if (command.action === 'set_timer' || command.action === 'set_reminder') {
        const mins = command.args?.minutes || 5;
        const label = command.args?.label || command.args?.text || 'Recordatorio de Bron';
        const ms = mins * 60 * 1000;
        setTimeout(() => {
            showNotification(`⏰ ${label}`);
            try {
                invokeTauri('run_shell_command', {
                    cmd: 'powershell.exe',
                    args: ['-NoProfile', '-Command', `Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('${label.replace(/'/g,"''")}','Bron - Recordatorio','OK','Information')`]
                });
            } catch(_) {}
        }, ms);
        return `⏰ Timer configurado: "${label}" en ${mins} minuto(s). Te aviso cuando se cumpla.`;
    }

    if (command.target === 'android') {
        if (!syncClient || !syncClient.isAuthenticated() || !syncClient.isPaired()) {
            return 'El telefono no esta listo para recibir ordenes: falta autenticacion o emparejamiento.';
        }
        const queued = await syncClient.sendRemoteCommand(command);
        if (!queued?.id) {
            return 'No pude encolar la orden remota para el telefono. El servidor no devolvio un ID de comando.';
        }
        const actionText = describeCommand(command);
        return command.requiresConfirmation
            ? `Mande al telefono la orden para ${actionText}. Como es una accion sensible, Bron va a preparar la pantalla y pedir confirmacion antes de enviar. ID: ${queued.id}.`
            : `Mande al telefono la orden para ${actionText}. ID: ${queued.id}.`;
    }

    const result = await executeWindowsCommand(command);
    return result.message;
}

function describeCommand(command) {
    if (command.action === 'daily_briefing') return 'generar briefing diario';
    if (command.action === 'agent_test') return 'ejecutar prueba integral';
    if (command.action === 'open_url') return `abrir ${command.args?.url || command.args?.app || 'enlace'}`;
    if (command.action === 'open_app') return `abrir ${command.args?.app || 'aplicacion'}`;
    if (command.action === 'compose_whatsapp') return 'preparar un WhatsApp';
    if (command.action === 'compose_email') return 'preparar un correo';
    if (command.action === 'send_email') return 'enviar un correo';
    if (command.action === 'create_calendar_event') return 'crear un evento de calendario';
    if (command.action === 'play_music') return `reproducir ${command.args?.query || 'musica'}`;
    if (command.action === 'open_maps') return `abrir mapa de ${command.args?.query || 'la busqueda'}`;
    if (command.action === 'check_weather') return `consultar clima de ${command.args?.query || ''}`;
    if (command.action === 'set_timer') return `poner timer de ${command.args?.minutes || 5} minutos`;
    if (command.action === 'set_reminder') return `crear recordatorio: ${command.args?.text || ''}`;
    if (command.action === 'web_search') return `buscar "${command.args?.query || ''}"`;
    if (command.action === 'find_file') return `buscar archivo "${command.args?.query || ''}"`;
    if (command.action === 'open_file') return `abrir carpeta ${command.args?.path || ''}`;
    if (command.action === 'media_control') return `control de musica: ${command.args?.action || 'pausa'}`;
    if (command.action === 'calculate') return `calcular ${command.args?.expression || ''}`;
    if (command.action === 'translate_text') return `traducir texto`;
    if (command.action === 'cleanup_temp') return 'limpiar archivos temporales';
    if (command.action === 'disk_analysis') return 'analizar disco';
    if (command.action === 'optimize') return 'optimizar sistema';
    if (command.action === 'take_screenshot') return 'capturar pantalla';
    return command.action;
}

async function executeWindowsCommand(command) {
    if (!state.permissionConsentAccepted) {
        throw new Error('Necesito tu permiso para hacer cosas en el computador.');
    }

    const script = buildWindowsActionScript(command);
    if (!script) throw new Error('Todavia no se como ejecutar esa accion en Windows.');

    await invokeTauri('run_shell_command', {
        cmd: 'powershell.exe',
        args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script]
    });

    return {
        ok: true,
        message: command.requiresConfirmation
            ? `Listo: prepare ${describeCommand(command)} en Windows. Te dejo la confirmacion final a la vista.`
            : `Listo: ejecute ${describeCommand(command)} en Windows.`
    };
}

function buildWindowsActionScript(command) {
    if (command.action === 'open_url') {
        return `Start-Process ${psQuote(command.args.url)}`;
    }

    if (command.action === 'open_app') {
        const app = command.args.app;
        const apps = {
            opera: [
                '$paths = @("$env:LOCALAPPDATA\\Programs\\Opera\\launcher.exe", "$env:ProgramFiles\\Opera\\launcher.exe", "${env:ProgramFiles(x86)}\\Opera\\launcher.exe")',
                '$exe = $paths | Where-Object { Test-Path $_ } | Select-Object -First 1',
                'if ($exe) { Start-Process $exe } else { Start-Process "opera" }'
            ].join('; '),
            chrome: 'Start-Process "chrome"',
            whatsapp: 'Start-Process "whatsapp:"',
            gmail: 'Start-Process "https://mail.google.com/"',
            calendar: 'Start-Process "https://calendar.google.com/"',
            drive: 'Start-Process "https://drive.google.com/"',
            docs: 'Start-Process "https://docs.google.com/document/u/0/"',
            sheets: 'Start-Process "https://docs.google.com/spreadsheets/u/0/"',
            contacts: 'Start-Process "https://contacts.google.com/"',
            spotify: 'Start-Process "spotify:"',
            music: 'Start-Process "https://music.youtube.com/"',
            youtube: 'Start-Process "https://www.youtube.com/"',
            instagram: 'Start-Process "https://www.instagram.com/"',
            tiktok: 'Start-Process "https://www.tiktok.com/"',
            twitter: 'Start-Process "https://www.twitter.com/"',
            facebook: 'Start-Process "https://www.facebook.com/"',
            telegram: 'Start-Process "tg:"',
            discord: 'Start-Process "discord:"',
            notion: 'Start-Process "https://www.notion.so/"',
            trello: 'Start-Process "https://trello.com/"',
            chatgpt: 'Start-Process "https://chat.openai.com/"',
            claude: 'Start-Process "https://claude.ai/"',
            netflix: 'Start-Process "https://www.netflix.com/"',
            amazon: 'Start-Process "https://www.amazon.com/"',
            linkedin: 'Start-Process "https://www.linkedin.com/"',
            reddit: 'Start-Process "https://www.reddit.com/"',
            twitch: 'Start-Process "https://www.twitch.tv/"',
            github: 'Start-Process "https://github.com/"',
            figma: 'Start-Process "https://www.figma.com/"',
            canva: 'Start-Process "https://www.canva.com/"',
            calculadora: 'Start-Process "calc.exe"',
            notepad: 'Start-Process "notepad.exe"',
            paint: 'Start-Process "mspaint.exe"',
            terminal: 'Start-Process "wt.exe"',
            explorer: 'Start-Process "explorer.exe"'
        };
        return apps[app] || null;
    }

    if (command.action === 'compose_whatsapp') {
        const message = encodeURIComponent(command.args.message || '');
        return `Start-Process "https://wa.me/?text=${message}"`;
    }

    if (command.action === 'compose_email') {
        const to = encodeURIComponent(command.args.to || '');
        const subject = encodeURIComponent(command.args.subject || '');
        const body = encodeURIComponent(command.args.body || '');
        return `Start-Process "https://mail.google.com/mail/?view=cm&fs=1&to=${to}&su=${subject}&body=${body}"`;
    }

    if (command.action === 'send_email') {
        const to = command.args.to || '';
        const subject = command.args.subject || '';
        const body = command.args.body || '';
        const fallbackTo = encodeURIComponent(to);
        const fallbackSubject = encodeURIComponent(subject);
        const fallbackBody = encodeURIComponent(body);
        return [
            '$ErrorActionPreference = "Stop"',
            `$to = ${psQuote(to)}`,
            `$subject = ${psQuote(subject)}`,
            `$body = ${psQuote(body)}`,
            'if (Get-Command gog -ErrorAction SilentlyContinue) {',
            '  gog gmail send --to $to --subject $subject --body $body --force --no-input',
            '} elseif (Get-Command maton -ErrorAction SilentlyContinue) {',
            '  maton google-mail message send --to $to --subject $subject --body $body',
            '} else {',
            `  Start-Process "https://mail.google.com/mail/?view=cm&fs=1&to=${fallbackTo}&su=${fallbackSubject}&body=${fallbackBody}"`,
            '}'
        ].join('; ');
    }

    if (command.action === 'create_calendar_event') {
        const title = encodeURIComponent(command.args.title || 'Nuevo evento');
        const details = encodeURIComponent(command.args.details || '');
        return `Start-Process "https://calendar.google.com/calendar/render?action=TEMPLATE&text=${title}&details=${details}&ctz=America%2FSantiago"`;
    }

    if (command.action === 'play_music') {
        const query = encodeURIComponent(command.args.query || '');
        if (command.args.service === 'spotify') {
            return query ? `Start-Process "spotify:search:${query}"` : 'Start-Process "spotify:"';
        }
        return query
            ? `Start-Process "https://music.youtube.com/search?q=${query}"`
            : 'Start-Process "https://music.youtube.com/"';
    }

    if (command.action === 'open_maps') {
        const query = encodeURIComponent(command.args.query || '');
        return `Start-Process "https://www.google.com/maps/search/?api=1&query=${query}"`;
    }

    // Web Search
    if (command.action === 'web_search') {
        const query = encodeURIComponent(command.args.query || '');
        return `Start-Process "https://www.google.com/search?q=${query}"`;
    }

    // File Manager
    if (command.action === 'find_file') {
        const query = command.args.query || '';
        return `$found = Get-ChildItem -Path "$env:USERPROFILE" -Recurse -Filter "*${query.replace(/"/g,'')}*" -ErrorAction SilentlyContinue | Select-Object -First 10 FullName, Length, LastWriteTime; if($found){$found | Format-Table -AutoSize}else{Write-Output "No se encontro: ${query.replace(/"/g,'')}"}`;
    }
    if (command.action === 'open_file') {
        const path = command.args.path || 'descargas';
        const folders = {
            descargas: '$env:USERPROFILE\\Downloads',
            downloads: '$env:USERPROFILE\\Downloads',
            documentos: '$env:USERPROFILE\\Documents',
            documents: '$env:USERPROFILE\\Documents',
            escritorio: '$env:USERPROFILE\\Desktop',
            desktop: '$env:USERPROFILE\\Desktop',
            imagenes: '$env:USERPROFILE\\Pictures',
            pictures: '$env:USERPROFILE\\Pictures',
        };
        const resolved = folders[path.toLowerCase()] || path;
        return `Start-Process "${resolved}"`;
    }

    // Media Control
    if (command.action === 'media_control') {
        const act = command.args.action || 'pause';
        if (act === 'pause' || act === 'play') {
            return 'Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.SendKeys]::SendWait("{MEDIA_PLAY_PAUSE}")';
        }
        if (act === 'next') {
            return 'Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.SendKeys]::SendWait("{MEDIA_NEXT_TRACK}")';
        }
        if (act === 'volume') {
            const level = parseInt(command.args.level) || 50;
            return `$wshShell = New-Object -ComObject WScript.Shell; 1..50 | ForEach-Object { $wshShell.SendKeys([char]174) }; 1..${Math.round(level/2)} | ForEach-Object { $wshShell.SendKeys([char]175) }; Write-Output "Volumen ajustado a ~${level}%"`;
        }
    }

    // System Cleanup
    if (command.action === 'cleanup_temp') {
        return [
            '$before = (Get-ChildItem $env:TEMP -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum / 1MB',
            'Remove-Item "$env:TEMP\\*" -Recurse -Force -ErrorAction SilentlyContinue',
            'Remove-Item "$env:LOCALAPPDATA\\Temp\\*" -Recurse -Force -ErrorAction SilentlyContinue',
            '$after = (Get-ChildItem $env:TEMP -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum / 1MB',
            '$freed = [math]::Round($before - $after, 1)',
            'Write-Output "Limpieza completada. Se liberaron $($freed)MB de archivos temporales."'
        ].join('; ');
    }
    if (command.action === 'disk_analysis') {
        return 'Get-PSDrive -PSProvider FileSystem | Select Name, @{N="UsadoGB";E={[math]::Round($_.Used/1GB,1)}}, @{N="LibreGB";E={[math]::Round($_.Free/1GB,1)}}, @{N="TotalGB";E={[math]::Round(($_.Used+$_.Free)/1GB,1)}} | Format-Table -AutoSize';
    }
    if (command.action === 'optimize') {
        return [
            'Write-Output "Optimizando..."',
            'Remove-Item "$env:TEMP\\*" -Recurse -Force -ErrorAction SilentlyContinue',
            'Remove-Item "$env:LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\thumbcache_*" -Force -ErrorAction SilentlyContinue',
            '$disk=Get-PSDrive C; Write-Output "Disco C: $([math]::Round($disk.Free/1GB,1))GB libres de $([math]::Round(($disk.Used+$disk.Free)/1GB,1))GB"',
            'Write-Output "Listo. Archivos temporales limpiados."'
        ].join('; ');
    }

    // Screenshot
    if (command.action === 'take_screenshot') {
        return 'Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; $bmp=[System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width,[System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height); $g=[System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen(0,0,0,0,$bmp.Size); $f="$env:TEMP\\bron_screen.png"; $bmp.Save($f); $g.Dispose(); $bmp.Dispose(); Write-Output "Screenshot guardado: $f"';
    }

    return null;
}

function psQuote(value) {
    return `'${String(value).replace(/'/g, "''")}'`;
}

function startCommandPolling() {
    if (window.__TAURI__) {
        return;
    }
    if (commandPollTimer || !syncClient || !syncClient.isAuthenticated()) return;
    commandPollTimer = setInterval(pollRemoteCommandsForWindows, 7000);
    pollRemoteCommandsForWindows();
}

function stopCommandPolling() {
    if (commandPollTimer) clearInterval(commandPollTimer);
    commandPollTimer = null;
}

async function pollRemoteCommandsForWindows() {
    if (!syncClient || !syncClient.isAuthenticated()) return;
    const processedKey = 'danbron_processed_commands';
    const processed = new Set(JSON.parse(localStorage.getItem(processedKey) || '[]'));

    try {
        const commands = await syncClient.getRemoteCommands(100);
        for (const command of commands) {
            if (!command.id || processed.has(command.id)) continue;
            if (command.target && !['windows', 'pc', 'desktop', 'paired'].includes(command.target)) continue;

            processed.add(command.id);
            localStorage.setItem(processedKey, JSON.stringify([...processed].slice(-100)));

            try {
                const result = await executeWindowsCommand(command);
                await syncClient.recordCommandResult(command.id, 'completed', result);
                showNotification(`Listo: ${describeCommand(command)}`);
            } catch (error) {
                await syncClient.recordCommandResult(command.id, 'failed', { error: error.message });
                showNotification(`No pude hacer eso: ${describeCommand(command)}`);
            }
        }
    } catch (error) {
        console.debug('Remote command polling failed:', error);
    }
}

function isPcContextQuestion(text) {
    const t = text.toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '');

    const contextTerms = [
        'abierto', 'abierta', 'ventana', 'ventanas', 'programa', 'programas',
        'app', 'apps', 'aplicacion', 'aplicaciones', 'pc', 'computador',
        'ordenador', 'whatsapp', 'wsp', 'gmail', 'outlook', 'juego', 'juegos'
    ];
    const askTerms = [
        'que tengo', 'que hay', 'que esta', 'que estan', 'que tienes',
        'dime', 'digas', 'sabes', 'viendo', 'ves', 'acceso'
    ];

    return contextTerms.some(term => t.includes(term)) && askTerms.some(term => t.includes(term));
}

async function answerPcContextQuestion(userMessage) {
    if (!state.permissionConsentAccepted) {
        return 'Todavia no tengo el permiso local aceptado en esta instalacion. Para ver ventanas, apps y contexto del PC tienes que aceptar el permiso obligatorio de Windows dentro de Danbron.';
    }

    const ok = await refreshPcContext(false);
    const ctx = state.systemContext || {};
    if (!ok && ctx.lastError) {
        return `Tengo el permiso, pero Windows no me dejo leer el contexto ahora mismo: ${ctx.lastError}. No voy a inventar lo que tienes abierto.`;
    }

    const windows = (ctx.activeWindows || [])
        .filter(w => w.windowTitle || w.processName)
        .slice(0, 10)
        .map(w => `${w.processName || 'app'}: ${w.windowTitle || 'sin titulo'}`);
    const comms = (ctx.comms || [])
        .slice(0, 6)
        .map(c => `${c.source}: ${c.title || c.processName || 'visible'}`);
    const runningGames = (ctx.games || [])
        .filter(g => g.running)
        .slice(0, 6)
        .map(g => g.name);
    const installedGames = (ctx.games || [])
        .filter(g => !g.running)
        .slice(0, 6)
        .map(g => g.name);

    const visibleText = windows.length
        ? `Ahora mismo Windows me muestra estas ventanas visibles: ${windows.join('; ')}.`
        : 'Ahora mismo Windows no me entrego ninguna ventana visible con titulo.';
    const commsText = comms.length
        ? `Comunicacion visible detectada: ${comms.join('; ')}.`
        : 'No veo WhatsApp, Gmail, Outlook u otra comunicacion visible en los titulos/procesos actuales.';
    const gamesText = runningGames.length
        ? `Juegos, video o distracciones activas detectadas: ${runningGames.join(', ')}.`
        : installedGames.length
        ? `No veo juegos activos; si aparecen instalados/detectables: ${installedGames.join(', ')}.`
        : 'No veo juegos o launchers activos en este momento.';

    return `${visibleText} ${commsText} ${gamesText} Esto viene del lector local autorizado de Windows; no voy a decir que algo esta abierto si no aparece ahi.`;
}

function handleClearChat() {
    state.messages = [];
    saveState();
    showChat();
}

async function togglePin(event) {
    event?.preventDefault?.();
    state.isPinned = !state.isPinned;
    $('pinBtn')?.classList.toggle('pinned', state.isPinned);
    await nativeWindowAction('pin', { pinned: state.isPinned });
    saveState();
}

function openSettings() {
    const syncStatus = state.isPaired 
        ? `Conectado con tu telefono`
        : state.isAuthenticated
        ? 'Listo para vincular tu telefono'
        : 'Usando solo en este computador';
    
    const syncStatusColor = state.isPaired ? '#10B981' : state.isAuthenticated ? '#F59E0B' : '#EF4444';
    
    $('syncStatus').textContent = syncStatus;
    $('syncStatus').style.color = syncStatusColor;
    
    // Update pairing status UI
    const $icon = $('pairingIcon');
    const $title = $('pairingTitle');
    const $subtitle = $('pairingSubtitle');
    const $actions = $('pairingActions');
    const $pairedInfo = $('pairedInfo');
    const $section = $('pairingStatusSection');

    if (state.isPaired) {
        if ($icon) $icon.textContent = '✅';
        if ($icon) $icon.style.background = 'rgba(16,185,129,0.15)';
        if ($title) { $title.textContent = `Conectado con ${state.pairedDeviceName || 'Android'}`; $title.style.color = '#10B981'; }
        if ($subtitle) $subtitle.textContent = `Ultima sincronizacion: ${new Date().toLocaleTimeString('es', {hour:'2-digit',minute:'2-digit'})}`;
        if ($section) $section.style.borderColor = 'rgba(16,185,129,0.4)';
        if ($actions) $actions.style.display = 'none';
        if ($pairedInfo) $pairedInfo.style.display = 'block';
    } else {
        if ($icon) $icon.textContent = '💻';
        if ($icon) $icon.style.background = 'rgba(232,184,75,0.1)';
        if ($title) { $title.textContent = 'Sin telefono vinculado'; $title.style.color = 'var(--text-primary)'; }
        if ($subtitle) $subtitle.textContent = 'Vincula tu Android para sincronizar todo';
        if ($section) $section.style.borderColor = 'var(--border)';
        if ($actions) $actions.style.display = 'block';
        if ($pairedInfo) $pairedInfo.style.display = 'none';
    }

    // Verify sync button
    const $verifyBtn = $('verifySyncBtn');
    if ($verifyBtn && !$verifyBtn.dataset.bound) {
        $verifyBtn.addEventListener('click', async () => {
            $verifyBtn.textContent = 'Verificando...';
            try {
                const paired = await syncClient.checkPairedDevice();
                if (paired) {
                    showNotification('Conexion verificada con ' + (paired.deviceName || 'Android'));
                } else {
                    showNotification('No se encontro dispositivo vinculado');
                    state.isPaired = false;
                    saveState();
                    openSettings();
                }
            } catch(e) { showNotification('Error al verificar'); }
            $verifyBtn.textContent = 'Verificar';
        });
        $verifyBtn.dataset.bound = '1';
    }

    // Unpair button
    const $unpairBtn = $('unpairBtn');
    if ($unpairBtn && !$unpairBtn.dataset.bound) {
        $unpairBtn.addEventListener('click', async () => {
            try { await syncClient.unpairDevice(); } catch(e) { console.warn('Backend unpair failed, clearing locally:', e); }
            // Always clear local state
            state.isPaired = false;
            state.pairedDeviceId = '';
            state.pairedDeviceName = '';
            if (syncClient) {
                syncClient.pairedDeviceId = null;
                localStorage.removeItem('danbron_paired_device_id');
            }
            saveState();
            showNotification('Dispositivo desvinculado');
            openSettings();
        });
        $unpairBtn.dataset.bound = '1';
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
        $logoutBtn.textContent = 'Salir de mi cuenta';
        $logoutBtn.onclick = () => { closeSettings(); logout(); };
        const $modal = $('settingsModal')?.querySelector('.modal-content');
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

// AI API
function formatProviderError(provider, response, body) {
    const detail = String(body || '').trim();
    const suffix = detail ? `: ${detail.slice(0, 500)}` : '';
    return `${provider} ${response.status}${suffix}`;
}

// ═══════════════════════════════════════════════════════
// DEEP THINKING ENGINE (Mixture-of-Agents)
// ═══════════════════════════════════════════════════════
function detectDeepThinkingNeed(text) {
    const t = text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    const len = text.trim().length;
    // Short messages are never deep
    if (len < 20) return false;
    // Explicit deep thinking trigger
    if (/\b(piensa bien|analiza|razona|deep think|piensalo|reflexiona)\b/.test(t)) return true;
    // Complex reasoning patterns
    const reasoning = /(por que|como funciona|explica|explicame|diferencia entre|compara|ventaja|desventaja|pros y contras|que opinas|que piensas|que me recomiendas|como puedo|deberia|conviene|mejor opcion|estrategia|plan para|ayudame a decidir|analisis|evalua)/.test(t);
    // Math / code patterns
    const mathCode = /(calcula|formula|ecuacion|algoritmo|programar?|codigo|funcion|variable|porcentaje|interes compuesto|proyeccion|estimacion|cuanto.*si.*entonces|si.*ahorro|si.*gano|si.*invierto)/.test(t);
    // Long complex questions (40+ words)
    const wordCount = text.trim().split(/\s+/).length;
    const isLongQuestion = wordCount >= 40;
    // Planning
    const planning = /(plan |planifica|organiza mi|haz un plan|como organizo|paso a paso|guia para|tutorial|ensenameenseñame)/.test(t);
    return reasoning || mathCode || isLongQuestion || planning;
}

async function rawDeepAICall(messages) {
    if (isTauri()) {
        try {
            const text = await invokeTauri('proxy_ai_deep_request', {
                messagesJson: JSON.stringify(messages)
            });
            return text || null;
        } catch (e) {
            console.warn('[DeepThink] Deep call failed, falling back:', e.message);
            return null;
        }
    }
    return null; // Deep thinking only available in Tauri mode
}

// ═══════════════════════════════════════════════════════
// AGENTIC EXECUTION ENGINE - Bron can think, act, observe, repeat
// ═══════════════════════════════════════════════════════
const AGENT_MAX_ITERATIONS = 8;
const AGENT_TOOL_TIMEOUT = 30000;

async function callAI(userMessage, retryCount = 0) {
    const MAX_RETRIES = 3;
    
    const systemPrompt = buildSystemPrompt();
    const history = state.messages.slice(-12);

    const messages = [
        { role: 'system', content: systemPrompt },
        ...history.map(m => ({ role: m.role, content: m.content })),
    ];

    try {
        // Run the agentic loop instead of a single call
        const finalReply = await agenticLoop(messages);
        updateConnectionStatus('online');
        return finalReply || 'Sin respuesta';
    } catch (error) {
        if (error.message?.includes('fetch') || error.message?.includes('network') || error.message?.includes('Failed')) {
            updateConnectionStatus('offline');
            if (retryCount < MAX_RETRIES) {
                const wait = Math.pow(2, retryCount) * 1500;
                await new Promise(r => setTimeout(r, wait));
                return callAI(userMessage, retryCount + 1);
            }
        }
        throw error;
    }
}

// Smart detection: does the user's message need tool use?
function detectToolNeed(text) {
    const t = text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    const pcKeywords = ['computador','pc','compu','laptop','bateria','ram','disco','memoria','proceso','app','aplicacion','ventana','abierto','abierta','camara','pantalla','captura','screenshot','archivo','carpeta','wifi','red','internet','espacio','temperatura','cpu','gpu'];
    const actionKeywords = ['abre','abrelo','abrir','ejecuta','instala','busca','revisa','muestra','muestrame','dime','crea','borra','cierra','reinicia'];
    const visionKeywords = ['foto','mirame','como me veo','que tengo en la cara','que ves','veme','sacame','retrato','selfie','cara','lentes','vestido','ropa'];
    const screenVisionKeywords = ['que nivel','que rango','que estoy jugando','que sale en','que hay en pantalla','que estoy viendo','dime que hay','league','lol ','fortnite','valorant','dota','juego','que pagina','que tab'];
    const hasVision = visionKeywords.some(k => t.includes(k));
    if (hasVision) return 'webcam_vision';
    const hasScreenVision = screenVisionKeywords.some(k => t.includes(k));
    if (hasScreenVision) return 'screen_vision';
    const hasPcWord = pcKeywords.some(k => t.includes(k));
    const hasAction = actionKeywords.some(k => t.includes(k));
    if (hasPcWord && hasAction) return 'pc_query';
    if (/\b(abre|abrir|abrelo|pon|reproduce|play)\b/.test(t) && /(youtube|spotify|chrome|navegador|musica|juego|game|whatsapp|maps|mapa)/.test(t)) return 'open_something';
    return null;
}

// Command library: ready-made PowerShell for common requests
function getCommandHint(need, userText) {
    const t = userText.toLowerCase();
    if (t.includes('bateria') || t.includes('carga')) return 'Get-CimInstance Win32_Battery | Select EstimatedChargeRemaining, BatteryStatus';
    if (t.includes('ram') || t.includes('memoria')) return '$os=Get-CimInstance Win32_OperatingSystem; Write-Output "RAM usada: $([math]::Round(($os.TotalVisibleMemorySize-$os.FreePhysicalMemory)/1MB,1))GB de $([math]::Round($os.TotalVisibleMemorySize/1MB,1))GB"';
    if (t.includes('disco') || t.includes('espacio')) return 'Get-PSDrive -PSProvider FileSystem | Select Name, @{N="UsadoGB";E={[math]::Round($_.Used/1GB,1)}}, @{N="LibreGB";E={[math]::Round($_.Free/1GB,1)}} | Format-Table';
    if (t.includes('proceso') || t.includes('abierto') || t.includes('ventana') || t.includes('app')) return 'Get-Process | Where-Object {$_.MainWindowTitle -ne ""} | Select-Object ProcessName, MainWindowTitle | Format-Table -AutoSize';
    if (t.includes('cpu') || t.includes('procesador')) return 'Get-CimInstance Win32_Processor | Select Name, NumberOfCores, MaxClockSpeed; Get-Counter "\\Processor(_Total)\\% Processor Time" | Select -ExpandProperty CounterSamples | Select CookedValue';
    if (t.includes('wifi') || t.includes('red') || t.includes('internet')) return 'Get-NetAdapter | Where Status -eq Up | Select Name, LinkSpeed; Test-Connection google.com -Count 2 | Select ResponseTime';
    if (t.includes('camara') || t.includes('vestido') || t.includes('foto')) return '$cam=Get-PnpDevice -Class Camera -Status OK | Select -First 1 -ExpandProperty FriendlyName; if(!$cam){Write-Output "No hay camara detectada";exit}; $out="$env:TEMP\\bron_webcam.jpg"; try{ $ffmpeg=Get-Command ffmpeg -ErrorAction SilentlyContinue; if($ffmpeg){ & ffmpeg -f dshow -i video="$cam" -frames:v 1 -y $out 2>$null; Write-Output "Foto capturada: $out" } else { Add-Type -AssemblyName System.Windows.Forms; Start-Process "microsoft.windows.camera:" -ErrorAction SilentlyContinue; Start-Sleep 2; Add-Type -AssemblyName System.Drawing; $bmp=[System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width,[System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height); $g=[System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen(0,0,0,0,$bmp.Size); $bmp.Save($out); $g.Dispose(); $bmp.Dispose(); Write-Output "Captura de pantalla con camara abierta: $out" } }catch{ Write-Output "Error al capturar: $_" }';
    if (t.includes('pantalla') || t.includes('screenshot') || t.includes('captura') || t.includes('viendo') || t.includes('ves')) return 'Add-Type -AssemblyName System.Windows.Forms; $bmp = [System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width, [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height); $g = [System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen(0,0,0,0,$bmp.Size); $f = "$env:TEMP\\bron_screen.png"; $bmp.Save($f); $g.Dispose(); $bmp.Dispose(); $f';
    if (t.includes('texto en pantalla') || t.includes('leer pantalla') || t.includes('ocr')) return 'Add-Type -AssemblyName System.Windows.Forms; $bmp = [System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width, [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height); $g = [System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen(0,0,0,0,$bmp.Size); $f = "$env:TEMP\\bron_screen.png"; $bmp.Save($f); $g.Dispose(); $bmp.Dispose(); Add-Type -Path "C:\\Windows\\Microsoft.NET\\assembly\\GAC_MSIL\\Microsoft.Windows.Winmd\\*\\Microsoft.Windows.Winmd" -ErrorAction SilentlyContinue; Write-Output "Screenshot guardado en $f"';
    if (t.includes('temperatura') || t.includes('clima') || t.includes('pronostico')) return `(Invoke-WebRequest -Uri "https://wttr.in/?format=%C+%t+%h+%w&lang=es" -TimeoutSec 5).Content`;
    if (t.includes('hora') && (t.includes('que') || t.includes('dime'))) return 'Get-Date -Format "dddd dd/MM/yyyy HH:mm:ss"';
    if (t.includes('apaga') || t.includes('reinicia') || t.includes('suspende')) return null; // block dangerous commands without confirmation
    if (t.includes('usuario') || t.includes('quien soy')) return '$env:USERNAME; $env:COMPUTERNAME; (Get-CimInstance Win32_OperatingSystem).Caption';
    if (t.includes('bluetooth')) return 'Get-PnpDevice -Class Bluetooth -ErrorAction SilentlyContinue | Select FriendlyName, Status | Format-Table';
    if (t.includes('impresora') || t.includes('printer')) return 'Get-Printer -ErrorAction SilentlyContinue | Select Name, PrinterStatus, DriverName | Format-Table';
    if (t.includes('actualiza') || t.includes('update')) return 'Get-HotFix | Sort-Object InstalledOn -Descending | Select-Object -First 5 HotFixID, InstalledOn | Format-Table';
    if (t.includes('ip') || t.includes('direccion ip')) return 'Get-NetIPAddress -AddressFamily IPv4 | Where {$_.InterfaceAlias -notlike "*Loopback*"} | Select InterfaceAlias, IPAddress | Format-Table';
    if (need === 'pc_query') return '$os=Get-CimInstance Win32_OperatingSystem; $disk=Get-PSDrive C; $bat=Get-CimInstance Win32_Battery -ErrorAction SilentlyContinue; Write-Output "RAM: $([math]::Round(($os.TotalVisibleMemorySize-$os.FreePhysicalMemory)/1MB,1))GB / $([math]::Round($os.TotalVisibleMemorySize/1MB,1))GB"; Write-Output "Disco C: $([math]::Round($disk.Used/1GB,1))GB / $([math]::Round(($disk.Used+$disk.Free)/1GB,1))GB"; if($bat){Write-Output "Bateria: $($bat.EstimatedChargeRemaining)%"}';
    return null;
}

async function agenticLoop(messages) {
    let iterations = 0;
    const userMsg = messages[messages.length - 1]?.content || '';
    const toolNeed = detectToolNeed(userMsg);
    const needsDeepThinking = !toolNeed && detectDeepThinkingNeed(userMsg);

    while (iterations < AGENT_MAX_ITERATIONS) {
        iterations++;
        // Use deep thinking (MoA) for the first call on complex non-tool queries
        let rawReply;
        if (iterations === 1 && needsDeepThinking) {
            rawReply = await rawDeepAICall(messages);
            if (!rawReply) rawReply = await rawAICall(messages);
        } else {
            rawReply = await rawAICall(messages);
        }
        
        // Parse tool calls from the response
        const toolCalls = parseToolCalls(rawReply);
        
        if (toolCalls.length === 0) {
            // AI didn't use tools — check if it SHOULD have
            if (iterations === 1 && toolNeed) {
                // AI ignored tools. Inject a forceful retry with command hint
                const hint = getCommandHint(toolNeed, userMsg);
                messages.push({ role: 'assistant', content: rawReply });
                if (toolNeed === 'webcam_vision') {
                    messages.push({ role: 'user', content: `[SISTEMA]: Tu respuesta anterior NO uso herramientas. El usuario quiere que uses la CAMARA. Debes usar [TOOL:WEBCAM]describe a la persona: ropa, lentes, accesorios, pelo, expresion, fondo[/TOOL] para capturar y analizar. Responde SOLO con el marcador de herramienta.` });
                } else if (toolNeed === 'screen_vision') {
                    messages.push({ role: 'user', content: `[SISTEMA]: Tu respuesta anterior NO uso herramientas. El usuario quiere que LEAS SU PANTALLA. Debes usar [TOOL:VISION]describe detalladamente todo lo visible en la pantalla: app o juego abierto, textos, numeros, nivel, puntaje, nombres, menus, cualquier dato relevante[/TOOL] para capturar screenshot y analizarlo. Responde SOLO con el marcador de herramienta.` });
                } else {
                    messages.push({ role: 'user', content: `[SISTEMA]: Tu respuesta anterior NO uso herramientas. El usuario necesita datos REALES de su PC. Debes usar [TOOL:POWERSHELL] para ejecutar un comando. ${hint ? 'Usa este comando: [TOOL:POWERSHELL]' + hint + '[/TOOL]' : 'Ejecuta el comando apropiado con [TOOL:POWERSHELL]...[/TOOL].'} Responde SOLO con el marcador de herramienta.` });
                }
                continue;
            }
            return cleanToolMarkersFromReply(rawReply);
        }

        // Execute all tool calls and collect results
        const toolResults = [];
        for (const tool of toolCalls) {
            const result = await executeAgentTool(tool);
            toolResults.push({ tool: tool.type, command: tool.command?.slice(0, 200), result });
        }

        // Build the observation message and feed it back to the AI
        const observation = toolResults.map(r => 
            `[RESULTADO DE ${r.tool}]:\n${r.result}\n[/RESULTADO]`
        ).join('\n\n');

        // Add the AI's response (with tool calls) and the tool results to the conversation
        messages.push({ role: 'assistant', content: rawReply });
        messages.push({ role: 'user', content: `[SISTEMA - resultados reales de tus comandos]:\n${observation}\n\nAhora responde al usuario en español con los datos reales. Si necesitas mas info, usa herramientas de nuevo. Si ya terminaste, responde SIN marcadores.` });
    }

    // Safety: if we hit max iterations, return what we have
    return 'Ya hice varias cosas para resolver esto pero necesito que me des mas detalles para seguir.';
}

async function rawAICall(messages) {
    if (isTauri()) {
        const text = await invokeTauri('proxy_ai_request', {
            messagesJson: JSON.stringify(messages)
        });
        return text || 'Sin respuesta';
    }

    const result = await syncClient.aiChat({
        messages,
        systemPrompt: messages[0]?.content || '',
        maxTokens: 1200,
        temperature: 0.3
    });
    return result?.text || 'Sin respuesta';
}

function parseToolCalls(text) {
    const tools = [];
    
    // [TOOL:POWERSHELL]...[/TOOL]
    const psRegex = /\[TOOL:POWERSHELL\]([\s\S]*?)\[\/TOOL\]/gi;
    let match;
    while ((match = psRegex.exec(text)) !== null) {
        tools.push({ type: 'POWERSHELL', command: match[1].trim() });
    }

    // [TOOL:URL]...[/TOOL]
    const urlRegex = /\[TOOL:URL\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = urlRegex.exec(text)) !== null) {
        tools.push({ type: 'URL', command: match[1].trim() });
    }

    // [TOOL:TELEFONO]...[/TOOL]
    const phoneRegex = /\[TOOL:TELEFONO\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = phoneRegex.exec(text)) !== null) {
        try {
            tools.push({ type: 'TELEFONO', command: match[1].trim(), parsed: JSON.parse(match[1].trim()) });
        } catch (_) {
            tools.push({ type: 'TELEFONO', command: match[1].trim() });
        }
    }

    // [TOOL:NOTA]...[/TOOL]
    const noteRegex = /\[TOOL:NOTA\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = noteRegex.exec(text)) !== null) {
        tools.push({ type: 'NOTA', command: match[1].trim() });
    }

    // [TOOL:WEBCAM]optional prompt[/TOOL] — capture webcam photo
    const webcamRegex = /\[TOOL:WEBCAM\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = webcamRegex.exec(text)) !== null) {
        tools.push({ type: 'WEBCAM', command: match[1].trim() });
    }

    // [TOOL:VISION]prompt[/TOOL] — analyze last captured image
    const visionRegex = /\[TOOL:VISION\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = visionRegex.exec(text)) !== null) {
        tools.push({ type: 'VISION', command: match[1].trim() });
    }

    // [TOOL:CASA]action|entity_id[/TOOL] — Smart Home
    const casaRegex = /\[TOOL:CASA\]([\s\S]*?)\[\/TOOL\]/gi;
    while ((match = casaRegex.exec(text)) !== null) {
        tools.push({ type: 'CASA', command: match[1].trim() });
    }

    return tools;
}

async function executeAgentTool(tool) {
    try {
        switch (tool.type) {
            case 'POWERSHELL': return await executeAgentPowerShell(tool.command);
            case 'URL': return await executeAgentOpenURL(tool.command);
            case 'TELEFONO': return await executeAgentPhoneCommand(tool);
            case 'NOTA': return executeAgentSaveNote(tool.command);
            case 'WEBCAM': return await executeAgentWebcam(tool.command);
            case 'VISION': return await executeAgentVision(tool.command);
            case 'CASA': return await executeAgentSmartHome(tool.command);
            default: return `Herramienta desconocida: ${tool.type}`;
        }
    } catch (error) {
        return `Error ejecutando ${tool.type}: ${error.message}`;
    }
}

async function executeAgentPowerShell(command) {
    if (!isTauri()) return 'PowerShell no disponible (modo navegador).';
    if (!state.permissionConsentAccepted) return 'El usuario no ha dado permiso para ejecutar comandos.';

    // Safety: block obviously destructive commands
    const dangerous = /\b(Remove-Item|rm\s+-rf|del\s+\/|format|fdisk|Restart-Computer|Stop-Computer|shutdown)\b/i;
    if (dangerous.test(command)) {
        return 'BLOQUEADO: Ese comando podria ser destructivo. Pide confirmacion al usuario primero.';
    }

    try {
        const output = await Promise.race([
            invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command]
            }),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), AGENT_TOOL_TIMEOUT))
        ]);
        const result = String(output || '').trim();
        // Limit output size to avoid token explosion
        return result.slice(0, 3000) || '(comando ejecutado sin output)';
    } catch (error) {
        return `Error PowerShell: ${error.message}`;
    }
}

async function executeAgentOpenURL(url) {
    if (!url.startsWith('http')) url = 'https://' + url;
    try {
        if (isTauri()) {
            await invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-Command', `Start-Process "${url}"`]
            });
            return `URL abierta: ${url}`;
        }
        window.open(url, '_blank');
        return `URL abierta: ${url}`;
    } catch (error) {
        return `No pude abrir ${url}: ${error.message}`;
    }
}

async function executeAgentPhoneCommand(tool) {
    if (!syncClient?.isAuthenticated?.() || !syncClient?.isPaired?.()) {
        return 'Telefono no conectado. El usuario necesita vincular su telefono primero.';
    }
    try {
        const args = tool.parsed || { action: 'open_url', args: { url: tool.command } };
        const queued = await syncClient.sendRemoteCommand({
            target: 'android',
            action: args.action || 'open_url',
            args: args.args || {},
            requiresConfirmation: false
        });
        return queued?.id ? `Orden enviada al telefono (ID: ${queued.id})` : 'No se pudo enviar la orden.';
    } catch (error) {
        return `Error enviando al telefono: ${error.message}`;
    }
}

function executeAgentSaveNote(content) {
    const note = { title: 'Nota de Bron', content, created: new Date().toISOString() };
    state.notes.push(note);
    saveState();
    scheduleSharedSync('agent-note');
    return `Nota guardada: "${content.slice(0, 100)}"`;
}

// Last captured image path for chaining webcam → vision
let lastCapturedImagePath = '';

async function executeAgentWebcam(prompt) {
    if (!isTauri()) return 'Webcam no disponible en modo navegador.';
    
    // Step 1: Capture webcam photo using ffmpeg or screenshot fallback
    const captureScript = [
        '$cam = Get-PnpDevice -Class Camera -Status OK -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FriendlyName',
        '$outFile = "$env:TEMP\\bron_webcam.jpg"',
        'if (!$cam) { Write-Output "NO_CAMERA"; exit }',
        '$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue',
        'if ($ffmpeg) {',
        '  & ffmpeg -f dshow -i video="$cam" -frames:v 1 -y -q:v 2 $outFile 2>$null',
        '  if (Test-Path $outFile) {',
        '    $bytes = [System.IO.File]::ReadAllBytes($outFile)',
        '    $b64 = [Convert]::ToBase64String($bytes)',
        '    Write-Output "CAPTURED:$b64"',
        '  } else { Write-Output "CAPTURE_FAILED" }',
        '} else {',
        '  Start-Process "microsoft.windows.camera:" -ErrorAction SilentlyContinue',
        '  Start-Sleep -Seconds 3',
        '  Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing',
        '  $bmp = [System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width, [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height)',
        '  $g = [System.Drawing.Graphics]::FromImage($bmp)',
        '  $g.CopyFromScreen(0,0,0,0,$bmp.Size)',
        '  $bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Jpeg)',
        '  $g.Dispose(); $bmp.Dispose()',
        '  $bytes = [System.IO.File]::ReadAllBytes($outFile)',
        '  $b64 = [Convert]::ToBase64String($bytes)',
        '  Write-Output "CAPTURED:$b64"',
        '}'
    ].join('; ');

    try {
        const output = await Promise.race([
            invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', captureScript]
            }),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout capturing webcam')), 25000))
        ]);

        const result = String(output || '').trim();
        
        if (result === 'NO_CAMERA') return 'No se detectó una cámara conectada al computador.';
        if (result === 'CAPTURE_FAILED') return 'No pude capturar la foto. Verifica que la cámara no esté en uso.';
        
        if (result.startsWith('CAPTURED:')) {
            const base64 = result.substring(9);
            lastCapturedImagePath = base64;
            
            // Step 2: Send to vision AI for analysis
            const analysisPrompt = prompt || 'Describe detalladamente lo que ves en esta imagen. Si hay una persona, describe su apariencia, que lleva puesto (lentes, gorra, ropa, accesorios), su expresion facial, color de pelo. Describe tambien el fondo y ambiente. Responde en español casual como un amigo.';
            
            try {
                const analysis = await invokeTauri('proxy_ai_vision', {
                    imageBase64: base64,
                    prompt: analysisPrompt
                });
                return `[FOTO CAPTURADA Y ANALIZADA]\n${analysis}`;
            } catch (visionErr) {
                return `Capturé la foto pero no pude analizarla: ${visionErr}. La imagen está guardada en TEMP\\bron_webcam.jpg`;
            }
        }
        
        return 'No pude obtener la imagen de la cámara.';
    } catch (e) {
        return `Error capturando webcam: ${e.message}`;
    }
}

async function executeAgentVision(prompt) {
    if (!isTauri()) return 'Vision no disponible en modo navegador.';
    
    // If we have a recently captured image, use it
    if (lastCapturedImagePath) {
        try {
            const analysis = await invokeTauri('proxy_ai_vision', {
                imageBase64: lastCapturedImagePath,
                prompt: prompt || 'Describe lo que ves en esta imagen en español.'
            });
            return analysis;
        } catch (e) {
            return `Error analizando imagen: ${e}`;
        }
    }
    
    // Otherwise, take a screenshot and analyze that
    const screenshotScript = [
        'Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing',
        '$bmp = [System.Drawing.Bitmap]::new([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width, [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height)',
        '$g = [System.Drawing.Graphics]::FromImage($bmp)',
        '$g.CopyFromScreen(0,0,0,0,$bmp.Size)',
        '$f = "$env:TEMP\\bron_screen.jpg"',
        '$bmp.Save($f, [System.Drawing.Imaging.ImageFormat]::Jpeg)',
        '$g.Dispose(); $bmp.Dispose()',
        '$bytes = [System.IO.File]::ReadAllBytes($f)',
        '[Convert]::ToBase64String($bytes)'
    ].join('; ');
    
    try {
        const b64 = await Promise.race([
            invokeTauri('run_shell_command', {
                cmd: 'powershell.exe',
                args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', screenshotScript]
            }),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), 20000))
        ]);
        
        const base64 = String(b64 || '').trim();
        if (!base64 || base64.length < 100) return 'No pude capturar la pantalla.';
        
        lastCapturedImagePath = base64;
        const analysis = await invokeTauri('proxy_ai_vision', {
            imageBase64: base64,
            prompt: prompt || 'Describe lo que ves en la pantalla de este computador. Menciona apps abiertas, contenido visible, y cualquier detalle relevante. Responde en español.'
        });
        return `[PANTALLA ANALIZADA]\n${analysis}`;
    } catch (e) {
        return `Error con visión: ${e.message || e}`;
    }
}

async function executeAgentSmartHome(command) {
    if (!isTauri()) return 'Smart Home no disponible en modo navegador.';
    const haUrl = state.smartHomeUrl || '';
    const haToken = state.smartHomeToken || '';
    if (!haUrl || !haToken) return 'Smart Home no configurado. El usuario debe agregar la URL y token de Home Assistant en ajustes de perfil.';

    const parts = command.split('|', 2);
    const action = (parts[0] || '').trim().toLowerCase();
    const entityId = (parts[1] || '').trim();

    const actionMap = {
        'encender': 'turn_on', 'prender': 'turn_on', 'turn_on': 'turn_on',
        'apagar': 'turn_off', 'turn_off': 'turn_off',
        'toggle': 'toggle', 'cambiar': 'toggle',
        'listar': 'list', 'list': 'list', 'dispositivos': 'list',
        'estado': 'state', 'status': 'state'
    };
    const haAction = actionMap[action] || action;

    if (haAction === 'list') {
        const ps = `$r = Invoke-RestMethod -Uri "${haUrl}/api/states" -Headers @{Authorization="Bearer ${haToken}"} -Method Get -TimeoutSec 10; $r | Where-Object { $_.entity_id -match "^(light|switch|climate|lock|fan|cover)" } | Select-Object entity_id, @{N="name";E={$_.attributes.friendly_name}}, state | Format-Table -AutoSize`;
        return await executeAgentPowerShell(ps);
    }
    if (haAction === 'state' && entityId) {
        const ps = `$r = Invoke-RestMethod -Uri "${haUrl}/api/states/${entityId}" -Headers @{Authorization="Bearer ${haToken}"} -Method Get -TimeoutSec 10; Write-Output "$($r.attributes.friendly_name): $($r.state)"`;
        return await executeAgentPowerShell(ps);
    }

    const domain = entityId.split('.')[0] || 'homeassistant';
    const service = haAction;
    const ps = `$body = '{"entity_id":"${entityId}"}'; Invoke-RestMethod -Uri "${haUrl}/api/services/${domain}/${service}" -Headers @{Authorization="Bearer ${haToken}"; "Content-Type"="application/json"} -Method Post -Body $body -TimeoutSec 10; Write-Output "Ejecute ${service} en ${entityId}"`;
    return await executeAgentPowerShell(ps);
}

function cleanToolMarkersFromReply(text) {
    // Remove any leftover tool markers that weren't executed
    return text
        .replace(/\[TOOL:\w+\][\s\S]*?\[\/TOOL\]/gi, '')
        .replace(/\[RESULTADO DE \w+\]:[\s\S]*?\[\/RESULTADO\]/gi, '')
        .replace(/\[OBSERVACION DEL SISTEMA[\s\S]*$/gi, '')
        .trim() || 'Listo.';
}

// ═══════════════════════════════════════════════════════
// VOICE CONVERSATION ENGINE - Real-time speech with Bron
// ═══════════════════════════════════════════════════════
const voiceState = {
    active: false,
    listening: false,
    speaking: false,
    recognition: null,
    utterance: null,
    preferredVoice: null,
    continuous: true,
};

function initVoiceEngine() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    // If Web Speech API available (browser), use it
    if (SpeechRecognition) {
        const recognition = new SpeechRecognition();
        recognition.lang = 'es-CL';
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.maxAlternatives = 1;

        recognition.onresult = (event) => {
            let finalTranscript = '';
            let interimTranscript = '';
            for (let i = event.resultIndex; i < event.results.length; i++) {
                const result = event.results[i];
                if (result.isFinal) {
                    finalTranscript += result[0].transcript;
                } else {
                    interimTranscript += result[0].transcript;
                }
            }
            const transcriptEl = $('voiceTranscript');
            if (transcriptEl) {
                transcriptEl.textContent = finalTranscript || interimTranscript;
            }
            if (finalTranscript.trim()) {
                voiceState.listening = false;
                handleVoiceInput(finalTranscript.trim());
            }
        };

        recognition.onend = () => {
            if (voiceState.active && voiceState.listening && !voiceState.speaking) {
                try { recognition.start(); } catch (_) {}
            }
        };

        recognition.onerror = (event) => {
            if (event.error === 'no-speech' && voiceState.active) {
                try { recognition.start(); } catch (_) {}
            } else if (event.error !== 'aborted') {
                console.warn('Voice recognition error:', event.error);
            }
        };

        voiceState.recognition = recognition;
    } else if (isTauri()) {
        // Tauri fallback: use Windows System.Speech via PowerShell
        console.log('Using PowerShell speech recognition fallback for Tauri');
        voiceState.recognition = {
            _listening: false,
            start: async function() {
                this._listening = true;
                const transcriptEl = $('voiceTranscript');
                if (transcriptEl) transcriptEl.textContent = 'Escuchando...';
                try {
                    const result = await invokeTauri('run_shell_command', {
                        cmd: 'powershell.exe',
                        args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', [
                            'Add-Type -AssemblyName System.Speech',
                            '$recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine',
                            '$recognizer.SetInputToDefaultAudioDevice()',
                            '$grammar = New-Object System.Speech.Recognition.DictationGrammar',
                            '$recognizer.LoadGrammar($grammar)',
                            '$recognizer.InitialSilenceTimeout = [TimeSpan]::FromSeconds(5)',
                            '$recognizer.EndSilenceTimeout = [TimeSpan]::FromSeconds(2)',
                            'try {',
                            '  $result = $recognizer.Recognize([TimeSpan]::FromSeconds(10))',
                            '  if ($result) { Write-Output $result.Text }',
                            '  else { Write-Output "" }',
                            '} catch { Write-Output "" }',
                            'finally { $recognizer.Dispose() }'
                        ].join('; ')]
                    });
                    this._listening = false;
                    const text = String(result || '').trim();
                    if (transcriptEl) transcriptEl.textContent = text || '';
                    if (text && voiceState.active) {
                        voiceState.listening = false;
                        handleVoiceInput(text);
                    } else if (voiceState.active && voiceState.listening) {
                        // No result, restart
                        this.start();
                    }
                } catch (e) {
                    this._listening = false;
                    console.warn('PowerShell speech recognition failed:', e);
                    if (voiceState.active && voiceState.listening) {
                        setTimeout(() => this.start(), 500);
                    }
                }
            },
            stop: function() { this._listening = false; },
            abort: function() { this._listening = false; }
        };
    } else {
        console.warn('Speech Recognition not supported');
        return false;
    }

    // Find best Spanish voice for TTS
    loadPreferredVoice();
    if (window.speechSynthesis.onvoiceschanged !== undefined) {
        window.speechSynthesis.onvoiceschanged = loadPreferredVoice;
    }

    return true;
}

function loadPreferredVoice() {
    const voices = window.speechSynthesis.getVoices();
    // Prefer Microsoft Online (neural) voices — they sound much more natural
    // Then fallback to any Spanish voice
    voiceState.preferredVoice =
        voices.find(v => v.name?.includes('Online') && v.lang?.startsWith('es-MX')) ||
        voices.find(v => v.name?.includes('Online') && v.lang?.startsWith('es')) ||
        voices.find(v => v.name?.includes('Sabina') || v.name?.includes('Jorge')) ||
        voices.find(v => v.lang === 'es-MX') ||
        voices.find(v => v.lang === 'es-CL') ||
        voices.find(v => v.lang === 'es-ES') ||
        voices.find(v => v.lang?.startsWith('es')) ||
        voices.find(v => v.default) ||
        voices[0] || null;
    if (voiceState.preferredVoice) {
        console.log('TTS voice:', voiceState.preferredVoice.name, voiceState.preferredVoice.lang);
    }
}

function startVoiceMode() {
    if (!voiceState.recognition && !initVoiceEngine()) {
        showNotification('Tu navegador no soporta voz. Usa Chrome o Edge.');
        return;
    }

    voiceState.active = true;
    const overlay = $('voiceOverlay');
    if (overlay) overlay.classList.add('active');
    
    $('voiceBtn')?.classList.add('voice-active');
    setVoiceUIState('listening');
    startListening();
}

function stopVoiceMode() {
    voiceState.active = false;
    voiceState.listening = false;
    voiceState.speaking = false;

    try { voiceState.recognition?.abort(); } catch (_) {}
    try { window.speechSynthesis.cancel(); } catch (_) {}

    const overlay = $('voiceOverlay');
    if (overlay) overlay.classList.remove('active');
    
    $('voiceBtn')?.classList.remove('voice-active');
    $('voiceTranscript') && ($('voiceTranscript').textContent = '');
}

function startListening() {
    if (!voiceState.active || !voiceState.recognition) return;
    voiceState.listening = true;
    voiceState.speaking = false;
    setVoiceUIState('listening');
    try {
        voiceState.recognition.start();
    } catch (e) {
        // Already started, ignore
        if (!e.message?.includes('already started')) console.warn('Voice start error:', e);
    }
}

function setVoiceUIState(mode) {
    const orb = $('voiceOrb');
    const status = $('voiceStatus');
    if (!orb || !status) return;

    orb.className = 'voice-orb ' + mode;
    const labels = {
        listening: 'Escuchando...',
        thinking: 'Pensando...',
        speaking: 'Bron esta hablando...',
    };
    status.textContent = labels[mode] || '';
}

async function handleVoiceInput(text) {
    setVoiceUIState('thinking');
    $('voiceTranscript') && ($('voiceTranscript').textContent = text);

    // Add to chat history too
    state.messages.push({ role: 'user', content: text, time: formatTime(), timestamp: Date.now(), source: 'voice' });
    renderMessages();
    scrollToBottom();

    try {
        state.isTyping = true;
        showTyping();

        // Overall timeout: 50 seconds max for voice responses
        const VOICE_TIMEOUT = 50000;
        const timeoutPromise = new Promise((_, reject) => 
            setTimeout(() => reject(new Error('timeout')), VOICE_TIMEOUT)
        );

        const systemPrompt = buildSystemPrompt();
        const history = state.messages.slice(-12);
        const messages = [
            { role: 'system', content: systemPrompt },
            ...history.map(m => ({ role: m.role, content: m.content })),
        ];

        const reply = await Promise.race([agenticLoop(messages), timeoutPromise]);
        
        state.messages.push({ role: 'assistant', content: reply, time: formatTime(), timestamp: Date.now() });
        state.isTyping = false;
        hideTyping();
        saveState();
        renderMessages();
        scrollToBottom();

        // Speak the response
        await bronSpeak(reply);

        // After speaking, listen again (continuous conversation)
        if (voiceState.active && voiceState.continuous) {
            $('voiceTranscript') && ($('voiceTranscript').textContent = '');
            startListening();
        }
    } catch (err) {
        state.isTyping = false;
        hideTyping();
        const isTimeout = err?.message === 'timeout';
        if (isTimeout) {
            const sorry = 'Perdon, me tarde mucho. Preguntame de nuevo.';
            state.messages.push({ role: 'assistant', content: sorry, time: formatTime(), timestamp: Date.now() });
            saveState();
            renderMessages();
            if (voiceState.active) await bronSpeak(sorry);
        } else {
            showNotification('No pude responder. Intenta de nuevo.');
        }
        if (voiceState.active) {
            $('voiceTranscript') && ($('voiceTranscript').textContent = '');
            startListening();
        }
    }
}

function bronSpeak(text) {
    return new Promise((resolve) => {
        if (!window.speechSynthesis) {
            // Fallback: PowerShell TTS for Tauri
            if (isTauri()) {
                const clean = text.replace(/\*\*(.+?)\*\*/g, '$1').replace(/[*_#`\[\]]/g, '').replace(/https?:\/\/\S+/g, '').trim();
                if (!clean) { resolve(); return; }
                invokeTauri('run_shell_command', {
                    cmd: 'powershell.exe',
                    args: ['-NoProfile', '-Command', `Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Rate = 3; $s.Speak('${clean.replace(/'/g, "''").substring(0, 500)}'); $s.Dispose()`]
                }).then(resolve).catch(resolve);
                return;
            }
            resolve(); return;
        }

        // Clean text for speech: remove markdown, emojis, URLs
        const clean = text
            .replace(/\*\*(.+?)\*\*/g, '$1')
            .replace(/\*(.+?)\*/g, '$1')
            .replace(/`(.+?)`/g, '$1')
            .replace(/https?:\/\/\S+/g, '')
            .replace(/[*_#`]/g, '')
            .replace(/\[.*?\]/g, '')
            .trim();

        if (!clean) { resolve(); return; }

        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(clean);
        utterance.lang = 'es-MX';
        utterance.rate = 1.25;
        utterance.pitch = 1.05;
        utterance.volume = 1.0;
        if (voiceState.preferredVoice) utterance.voice = voiceState.preferredVoice;

        voiceState.speaking = true;
        voiceState.listening = false;
        setVoiceUIState('speaking');

        utterance.onend = () => {
            voiceState.speaking = false;
            resolve();
        };
        utterance.onerror = () => {
            voiceState.speaking = false;
            resolve();
        };

        // Chrome bug workaround: long texts get cut off
        if (clean.length > 200) {
            const sentences = clean.match(/[^.!?]+[.!?]+/g) || [clean];
            let i = 0;
            function speakNext() {
                if (i >= sentences.length || !voiceState.active) {
                    voiceState.speaking = false;
                    resolve();
                    return;
                }
                const u = new SpeechSynthesisUtterance(sentences[i].trim());
                u.lang = 'es-MX';
                u.rate = 1.25;
                u.pitch = 1.05;
                if (voiceState.preferredVoice) u.voice = voiceState.preferredVoice;
                u.onend = () => { i++; speakNext(); };
                u.onerror = () => { i++; speakNext(); };
                window.speechSynthesis.speak(u);
            }
            speakNext();
        } else {
            window.speechSynthesis.speak(utterance);
        }
    });
}

function buildSystemPrompt() {
    const now = new Date();
    const hour = now.getHours();
    const days = ['domingo','lunes','martes','miercoles','jueves','viernes','sabado'];
    const day = days[now.getDay()];
    const fecha = `${now.getDate()}/${now.getMonth()+1}/${now.getFullYear()}`;

    // Time intelligence
    let timeContext = '';
    if (hour >= 5 && hour < 9) timeContext = 'Es temprano, momento ideal para rutina matutina y planificacion.';
    else if (hour >= 9 && hour < 13) timeContext = 'Horario productivo de manana. Maxima concentracion.';
    else if (hour >= 13 && hour < 15) timeContext = 'Hora de almuerzo. Buen momento para descanso breve.';
    else if (hour >= 15 && hour < 19) timeContext = 'Tarde productiva. Segunda ola de enfoque.';
    else if (hour >= 19 && hour < 22) timeContext = 'Noche. Momento de cerrar pendientes y descansar.';
    else timeContext = 'Es muy tarde. Deberia estar durmiendo para rendir manana.';

    let syncInfo = '';
    if (state.isPaired && state.userProfile) {
        const p = state.userProfile;
        const challenges = Array.isArray(p.challenges) ? p.challenges.join(', ') : 'Ninguno';
        const debtRatio = p.debt > 0 && p.income > 0 ? ((p.debt / p.income) * 100).toFixed(0) : 0;
        const freeIncome = (p.income || 0) - (p.expenses || 0);
        syncInfo = `
[DATOS FINANCIEROS DEL USUARIO]:
- Nombre: ${p.name || 'Desconocido'}
- Ingresos mensuales: $${(p.income || 0).toLocaleString()}
- Gastos mensuales: $${(p.expenses || 0).toLocaleString()}
- Ingreso libre mensual: $${freeIncome.toLocaleString()}
- Deuda actual: $${(p.debt || 0).toLocaleString()} (${debtRatio}% de sus ingresos)
- Trabajo: ${p.workStyle || 'Desconocido'} / ${p.employmentStatus || 'Desconocido'}
- Retos actuales: ${challenges}

USA ESTOS NUMEROS EXACTOS. Cuando hables de dinero, calcula: si ahorra X por dia, en Y dias paga Z. Se MATEMATICAMENTE preciso.`;
    }

    let systemContextInfo = '';
    if (state.permissionConsentAccepted) {
        systemContextInfo = `
[ACCESO AL PC]: AUTORIZADO. Puedes ejecutar PowerShell libremente para obtener info del computador.
IMPORTANTE: NO tengo datos pre-cargados. Si el usuario pregunta sobre su PC (ventanas, bateria, RAM, disco, camara, etc.), USA [TOOL:POWERSHELL] para obtener la info REAL en ese momento. Nunca digas "veo que tienes X abierto" sin haberlo verificado con un comando.`;
    }

    return `Eres Bron, amigo personal del usuario. Hablas español latino casual, con calidez y humor. Respuestas cortas (3-5 oraciones) salvo que pidan detalle.

DATOS: Usuario=${state.userName || 'Amigo'}, Fecha=${day} ${fecha} ${hour}:${String(now.getMinutes()).padStart(2, '0')}, OS=Windows 11
${syncInfo}
${systemContextInfo}

TIENES HERRAMIENTAS REALES. Cuando el usuario pida algo sobre su PC, NO respondas con texto. EJECUTA comandos asi:

[TOOL:POWERSHELL]comando de Windows[/TOOL]
[TOOL:URL]https://ejemplo.com[/TOOL]
[TOOL:TELEFONO]{"action":"open_url","args":{"url":"https://ejemplo.com"}}[/TOOL]
[TOOL:NOTA]texto de la nota[/TOOL]
[TOOL:WEBCAM]pregunta opcional sobre lo que veo[/TOOL] — captura foto con la webcam y la analiza con IA de vision
[TOOL:VISION]que quiero saber de la imagen[/TOOL] — analiza la ultima imagen capturada o toma screenshot y lo analiza
[TOOL:CASA]accion|entity_id[/TOOL] — controlar dispositivos Smart Home (Home Assistant): encender, apagar, toggle, brillo, listar

MULTI-ACCION: Si el usuario pide VARIAS cosas en un mensaje, haz TODO en una sola respuesta. Ejemplo:
"abre la camara y sacame una foto" → usa [TOOL:WEBCAM]describe a la persona en la foto[/TOOL]
"abre youtube y pon musica" → [TOOL:POWERSHELL]Start-Process "https://youtube.com/results?search_query=musica+relajante"[/TOOL]
"que tengo abierto y cuanta bateria" → [TOOL:POWERSHELL]Get-Process | Where-Object {$_.MainWindowTitle -ne ''} | Select-Object ProcessName, MainWindowTitle | Format-Table -AutoSize; $bat=Get-CimInstance Win32_Battery; "Bateria: $($bat.EstimatedChargeRemaining)%"[/TOOL]

VISION/CAMARA: Cuando el usuario diga "sacame foto", "que tengo en la cara", "mirame", "que ves", "que hay en mi cara", "como me veo":
- USA [TOOL:WEBCAM]describe detalladamente a la persona, lentes, ropa, expresion, fondo[/TOOL]
- El sistema captura la foto y un modelo de vision AI la analiza. Tu recibes la descripcion.
- Luego responde al usuario naturalmente con lo que VES: "Veo que tienes puestos unos lentes de sol, una camiseta negra, y de fondo veo..."
- Habla en primera persona como si TU estuvieras viendo directamente.

LEER PANTALLA / JUEGOS / APPS: Si el usuario pregunta "que nivel soy en LoL", "que sale en mi pantalla", "que estoy jugando", "dime que hay en pantalla":
- USA [TOOL:VISION]describe detalladamente todo lo visible: app abierta, texto, numeros, nivel, puntaje, nombres, botones, menus, cualquier dato relevante[/TOOL]
- El sistema toma screenshot de la pantalla y un modelo de vision AI lo analiza. Tu recibes la descripcion.
- Responde con los datos concretos que ves: "Estas en League of Legends, nivel 45, estas en partida ranked..."
- FUNCIONA CON CUALQUIER APP O JUEGO: Uber, LoL, Chrome, Excel, Spotify, etc.

EJEMPLOS:
- "que apps tengo abiertas" → [TOOL:POWERSHELL]Get-Process | Where-Object {$_.MainWindowTitle -ne ''} | Select-Object ProcessName, MainWindowTitle | Format-Table -AutoSize[/TOOL]
- "revisa mi PC" → [TOOL:POWERSHELL]$ram=Get-CimInstance Win32_OperatingSystem; $disk=Get-PSDrive C; $bat=Get-CimInstance Win32_Battery; Write-Output "RAM: $([math]::Round(($ram.TotalVisibleMemorySize-$ram.FreePhysicalMemory)/1MB,1))GB / $([math]::Round($ram.TotalVisibleMemorySize/1MB,1))GB total"; Write-Output "Disco C: $([math]::Round($disk.Used/1GB,1))GB usado / $([math]::Round(($disk.Used+$disk.Free)/1GB,1))GB total"; Write-Output "Bateria: $($bat.EstimatedChargeRemaining)%"[/TOOL]
- "sacame una foto y dime que ves" → [TOOL:WEBCAM]describe a la persona: ropa, lentes, accesorios, pelo, expresion, fondo[/TOOL]
- "que hay en mi pantalla" → [TOOL:VISION]describe todo lo visible en la pantalla[/TOOL]
- "que nivel soy en LoL" → [TOOL:VISION]identifica el juego, nombre de invocador, nivel, rango, y cualquier dato visible del jugador[/TOOL]
- "que estoy viendo en chrome" → [TOOL:VISION]describe la pagina web abierta, su titulo, contenido visible y cualquier dato relevante[/TOOL]
- "cuanto gane en uber" → [TOOL:VISION]lee los numeros de ganancias, viajes completados, y cualquier dato financiero visible en pantalla[/TOOL]

REGLA CRITICA: Si el usuario pregunta CUALQUIER COSA sobre su computador, SIEMPRE usa herramientas. NUNCA inventes datos. Cuando pida foto/camara/mirame, SIEMPRE usa [TOOL:WEBCAM]. Cuando pregunte por datos de pantalla/juegos/apps, USA [TOOL:VISION]. Cuando pida varias cosas, hazlas TODAS en una respuesta.

SKILLS INSTALADAS (puedes usar todas):
- Clima: "que clima hace", "temperatura", "pronostico" → consulta wttr.in
- Timer/Recordatorios: "recuerdame en 10 min", "timer 5 minutos", "alarma"
- Busqueda web: "busca X", "google X", "precio de X", "noticias"
- Archivos: "busca archivo X", "abre descargas", "abre documentos"
- Musica: "pon musica", "spotify", "pausa", "siguiente cancion", "volumen 80"
- Calculadora: "cuanto es 500*12", "calcula 15% de 3000"
- Traductor: "traduce X al ingles", "como se dice X en ingles"
- Limpieza: "limpia temp", "optimiza mi PC", "libera espacio"
- Screenshot/OCR: "captura pantalla", "que hay en pantalla"
- Redes sociales: "abre instagram/tiktok/twitter/facebook"
- Apps: "abre spotify/netflix/discord/notion/github/figma/canva/youtube/whatsapp/telegram"
- Email: "envia correo a X", "abre gmail"
- Calendario: "agenda reunion", "crea evento"
- Mapas: "navega a X", "mapa de X"
- Briefing: "resumen del dia", "que tengo hoy"

Cuando el usuario pida algo que coincide con una skill, el sistema la ejecuta automaticamente. No necesitas explicar como funcionan.

REGLAS:
- Nunca digas "no puedo". Si no sabes, intenta con PowerShell.
- Si un comando falla, intenta otra forma.
- Para borrar archivos o apagar: pide confirmacion primero.
- Nunca menciones: backend, API, token, modelo de IA, OpenRouter, ni nada tecnico.
- El usuario solo ve tu respuesta final, no los comandos.
- Habla como amigo, nunca como robot.`;
}

// Markdown-like rendering for Bron messages
function renderMarkdown(text) {
    return text
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.+?)\*/g, '<em>$1</em>')
        .replace(/`(.+?)`/g, '<code>$1</code>')
        .replace(/\$([\d,.]+)/g, '<span class="money">\$$1</span>')
        .replace(/\n/g, '<br>');
}

function renderMessages() {
    const container = $('messagesContainer');
    if (!container) return;
    container.innerHTML = '';
    state.messages.forEach(msg => {
        const div = document.createElement('div');
        div.className = `message ${msg.role}`;

        const content = document.createElement('span');
        content.className = 'message-text';
        if (msg.role === 'assistant') {
            content.innerHTML = renderMarkdown(msg.content);
        } else {
            content.textContent = msg.content;
        }
        div.appendChild(content);

        // Timestamp + copy button
        const footer = document.createElement('div');
        footer.className = 'message-footer';
        
        if (msg.time) {
            const ts = document.createElement('span');
            ts.className = 'timestamp';
            ts.textContent = msg.time;
            footer.appendChild(ts);
        }

        if (msg.role === 'assistant') {
            const copyBtn = document.createElement('button');
            copyBtn.className = 'msg-copy-btn';
            copyBtn.textContent = 'Copiar';
            copyBtn.onclick = () => {
                navigator.clipboard.writeText(msg.content);
                copyBtn.textContent = 'âœ“';
                setTimeout(() => copyBtn.textContent = 'Copiar', 1500);
            };
            footer.appendChild(copyBtn);
        }

        div.appendChild(footer);
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

// Mascot Animation Engine
let globalTime = 0;
let mascotAnimationStarted = false;

function startMascotAnimation() {
    if (mascotAnimationStarted) return;
    mascotAnimationStarted = true;
    requestAnimationFrame(animateMascots);
}

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

// Persistence
function saveState() {
    localStorage.setItem('danbron_desktop', JSON.stringify({
        email: state.email,
        userName: state.userName,
        userId: state.userId,
        deviceId: state.deviceId,
        pairedDeviceId: state.pairedDeviceId,
        pairedDeviceName: state.pairedDeviceName,
        userProfile: state.userProfile,
        notes: state.notes || [],
        habits: state.habits || [],
        habitLogs: state.habitLogs || [],
        systemContext: state.systemContext,
        messages: state.messages.slice(-50),
        isAuthenticated: state.isAuthenticated,
        isPaired: state.isPaired,
        syncMode: state.syncMode,
        permissionConsentAccepted: state.permissionConsentAccepted,
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
            state.notes = d.notes || [];
            state.habits = d.habits || [];
            state.habitLogs = d.habitLogs || [];
            state.systemContext = d.systemContext || null;
            state.messages = (d.messages || []).map((message, idx, list) => ({
                ...message,
                timestamp: toMillis(message.timestamp, Date.now() - (list.length - idx) * 1000)
            }));
            state.isAuthenticated = d.isAuthenticated || false;
            state.isPaired = d.isPaired || false;
            state.syncMode = d.syncMode || 'backend';
            state.permissionConsentAccepted = d.permissionConsentAccepted !== false;
        }
    } catch (_) {}
}

function reconcileBackendSession() {
    if (!syncClient) return;

    if (syncClient.token && syncClient.deviceId) {
        state.isAuthenticated = true;
        state.userId = state.userId || syncClient.userId || '';
        state.deviceId = state.deviceId || syncClient.deviceId || '';
    }

    if (syncClient.pairedDeviceId) {
        state.isPaired = true;
        state.pairedDeviceId = state.pairedDeviceId || syncClient.pairedDeviceId;
    }
}

async function loadBackendSessionFromDisk() {
    if (!syncClient) return;
    if (syncClient.token && syncClient.deviceId) return;

    try {
        const script = [
            '$path = Join-Path $env:LOCALAPPDATA "Danbron\\session.json"',
            'if (Test-Path $path) { Get-Content -LiteralPath $path -Raw }'
        ].join('; ');
        const raw = await invokeTauri('run_shell_command', {
            cmd: 'powershell.exe',
            args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script]
        });
        if (!raw) return;

        const session = JSON.parse(raw);
        syncClient.token = session.token || syncClient.token;
        syncClient.userId = session.userId || syncClient.userId;
        syncClient.deviceId = session.deviceId || syncClient.deviceId;
        syncClient.pairedDeviceId = session.pairedDeviceId || syncClient.pairedDeviceId;

        if (syncClient.token) localStorage.setItem('danbron_auth_token', syncClient.token);
        if (syncClient.userId) localStorage.setItem('danbron_user_id', syncClient.userId);
        if (syncClient.deviceId) localStorage.setItem('danbron_device_id', syncClient.deviceId);
        if (syncClient.pairedDeviceId) localStorage.setItem('danbron_paired_device_id', syncClient.pairedDeviceId);

        state.email = session.email || state.email;
        state.userName = session.name || state.userName;
        state.permissionConsentAccepted = session.permissionConsentAccepted ?? state.permissionConsentAccepted;
    } catch (error) {
        console.debug('No disk session available:', error);
    }
}

// Connection Status
function updateConnectionStatus(status) {
    const dot = $('connectionDot');
    const text = $('connectionText');
    const bar = $('connectionBar');
    if (!dot || !text || !bar) return;

    connectionStatus = status;

    bar.className = `connection-bar ${status}`;
    dot.className = `connection-dot ${status}`;

    const labels = {
        online: '',
        offline: 'Reconectando...',
        connecting: ''
    };
    text.textContent = labels[status] || '';

    // Only show bar when offline (user doesn't need to know technical status)
    if (status === 'offline') {
        bar.classList.remove('hidden');
    } else {
        bar.classList.add('hidden');
    }
}
}
