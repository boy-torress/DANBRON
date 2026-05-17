/**
 * Windows System Events Tracker
 * Monitors app usage, emails, and system activity
 * Uses Tauri to access Windows APIs and PowerShell commands
 * Note: invokeTauri is defined globally in app.js — do NOT redeclare here
 */

class WindowsSystemTracker {
  constructor(syncClient) {
    this.syncClient = syncClient;
    this.trackedApps = new Set();
    this.eventCheckInterval = 60000; // Check every minute
    this.isMonitoring = false;
    this.currentContext = null;
  }

  /**
   * Start monitoring system events
   */
  async startMonitoring() {
    if (this.isMonitoring) return;

    this.isMonitoring = true;
    console.log('Starting Windows system monitoring...');

    // Monitor app usage every minute
    this.appMonitorInterval = setInterval(() => {
      this.checkOpenApplications();
    }, this.eventCheckInterval);

    // Check for emails every 5 minutes
    this.emailCheckInterval = setInterval(() => {
      this.checkForNewEmails();
    }, 5 * this.eventCheckInterval);

    // Check battery status every 30 seconds
    this.batteryCheckInterval = setInterval(() => {
      this.checkBatteryStatus();
    }, 30000);

    // Check for pending tasks
    this.taskCheckInterval = setInterval(() => {
      this.checkPendingTasks();
    }, 2 * this.eventCheckInterval);

    // Initial checks
    await this.checkOpenApplications();
    await this.checkBatteryStatus();
    await this.getFullContext();
  }

  /**
   * Stop monitoring system events
   */
  stopMonitoring() {
    if (!this.isMonitoring) return;

    this.isMonitoring = false;
    clearInterval(this.appMonitorInterval);
    clearInterval(this.emailCheckInterval);
    clearInterval(this.batteryCheckInterval);
    clearInterval(this.taskCheckInterval);

    console.log('Stopped Windows system monitoring');
  }

  /**
   * Get currently open applications
   */
  async checkOpenApplications() {
    try {
      const psCommand = `Get-Process | Select-Object -Property ProcessName, MainWindowTitle | ConvertTo-Json`;
      const processes = await this.executePowerShell(psCommand);

      if (!processes) return;

      const processList = Array.isArray(processes) ? processes : [processes];

      for (const proc of processList) {
        if (!proc.MainWindowTitle || proc.ProcessName === 'explorer') continue;

        const processId = `${proc.ProcessName}:${proc.MainWindowTitle}`;

        if (!this.trackedApps.has(processId)) {
          this.trackedApps.add(processId);

          await this.syncClient.recordSystemEvent('app_opened', {
            processName: proc.ProcessName,
            windowTitle: proc.MainWindowTitle.substring(0, 100),
            timestamp: new Date().toISOString()
          });
        }
      }
    } catch (error) {
      console.error('Failed to check open applications:', error);
    }
  }

  async getActiveWindows() {
    const psCommand = `
      $ErrorActionPreference = 'SilentlyContinue'
      [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
      Get-Process |
        Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.Trim().Length -gt 0 } |
        Select-Object -First 40 -Property ProcessName, MainWindowTitle |
        ConvertTo-Json -Depth 3 -Compress
    `;

    const windows = await this.executePowerShell(psCommand);
    const list = Array.isArray(windows) ? windows : (windows ? [windows] : []);
    return list.map(w => ({
      processName: String(w.ProcessName || '').trim(),
      windowTitle: String(w.MainWindowTitle || '').trim().substring(0, 160)
    })).filter(w => w.processName && w.windowTitle);
  }

  async getInstalledApps() {
    const psCommand = `
      $paths = @(
        'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
        'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
        'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*'
      )
      Get-ItemProperty $paths -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName } |
        Sort-Object DisplayName -Unique |
        Select-Object -First 120 @{Name='Name';Expression={$_.DisplayName}}, DisplayVersion, Publisher |
        ConvertTo-Json -Depth 3
    `;

    const apps = await this.executePowerShell(psCommand);
    const list = Array.isArray(apps) ? apps : (apps ? [apps] : []);
    return list.map(app => ({
      name: String(app.Name || '').trim(),
      version: String(app.DisplayVersion || '').trim(),
      publisher: String(app.Publisher || '').trim()
    })).filter(app => app.name);
  }

  async getCommunicationSignals(activeWindows = []) {
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

  async getGamesAndLaunchers(activeWindows = [], installedApps = []) {
    const gameNeedles = [
      'steam', 'epic games', 'xbox', 'riot', 'valorant', 'league of legends',
      'minecraft', 'roblox', 'battle.net', 'ea app', 'ubisoft', 'rockstar',
      'fortnite', 'counter-strike', 'cs2', 'gta', 'call of duty', 'youtube'
    ];

    const running = activeWindows
      .filter(w => gameNeedles.some(n => `${w.processName} ${w.windowTitle}`.toLowerCase().includes(n)))
      .map(w => ({ name: w.windowTitle || w.processName, running: true }));

    const installed = installedApps
      .filter(app => gameNeedles.some(n => app.name.toLowerCase().includes(n)))
      .map(app => ({ name: app.name, running: false }));

    const byName = new Map();
    [...running, ...installed].forEach(item => {
      const key = item.name.toLowerCase();
      if (!byName.has(key) || item.running) byName.set(key, item);
    });

    return Array.from(byName.values()).slice(0, 30);
  }

  /**
   * Check for new emails (via Outlook)
   */
  async checkForNewEmails() {
    try {
      // PowerShell command to check Outlook emails
      const psCommand = `
        try {
          $outlook = New-Object -ComObject Outlook.Application
          $namespace = $outlook.GetNamespace("MAPI")
          $inbox = $namespace.GetDefaultFolder(6) # 6 = olFolderInbox
          
          $unreadEmails = $inbox.Items | Where-Object {$_.UnRead} | Select-Object -First 5
          $unreadEmails | Select-Object -Property SenderName, Subject, ReceivedTime | ConvertTo-Json
        } catch {
          "No Outlook"
        }
      `;

      const emails = await this.executePowerShell(psCommand);

      if (!emails || emails === 'No Outlook') return;

      const emailList = Array.isArray(emails) ? emails : [emails];

      for (const email of emailList) {
        await this.syncClient.recordSystemEvent('email_received', {
          sender: email.SenderName.substring(0, 100),
          subject: email.Subject.substring(0, 200),
          receivedTime: email.ReceivedTime,
          timestamp: new Date().toISOString()
        });
      }
    } catch (error) {
      console.error('Failed to check emails:', error);
    }
  }

  /**
   * Check battery status
   */
  async checkBatteryStatus() {
    try {
      const psCommand = `
        $battery = Get-WmiObject -Class Win32_Battery
        if ($battery) {
          @{
            level = $battery.EstimatedChargeRemaining
            status = $battery.BatteryStatus
          } | ConvertTo-Json
        } else {
          "No battery detected"
        }
      `;

      const batteryInfo = await this.executePowerShell(psCommand);

      if (!batteryInfo || batteryInfo === 'No battery detected') return;

      await this.syncClient.recordSystemEvent('battery_status', {
        level: batteryInfo.level || 100,
        status: batteryInfo.status,
        timestamp: new Date().toISOString()
      });
      return batteryInfo;
    } catch (error) {
      console.error('Failed to check battery status:', error);
      return null;
    }
  }

  /**
   * Check for pending tasks/calendar events
   */
  async checkPendingTasks() {
    try {
      // PowerShell command to check Outlook calendar
      const psCommand = `
        try {
          $outlook = New-Object -ComObject Outlook.Application
          $namespace = $outlook.GetNamespace("MAPI")
          $calendar = $namespace.GetDefaultFolder(9) # 9 = olFolderCalendar
          
          $today = Get-Date
          $todayEvents = $calendar.Items | Where-Object {$_.Start -ge $today -and $_.Start -lt ($today.AddDays(1))}
          $todayEvents | Select-Object -Property Subject, Start | ConvertTo-Json
        } catch {
          "No Outlook calendar"
        }
      `;

      const events = await this.executePowerShell(psCommand);

      if (!events || events === 'No Outlook calendar') return;

      const eventList = Array.isArray(events) ? events : [events];

      for (const event of eventList) {
        await this.syncClient.recordSystemEvent('pending_task', {
          taskType: 'calendar_event',
          title: event.Subject.substring(0, 200),
          startTime: event.Start,
          timestamp: new Date().toISOString()
        });
      }
    } catch (error) {
      console.error('Failed to check pending tasks:', error);
    }
  }

  /**
   * Get system information
   */
  async getSystemInfo() {
    try {
      const psCommand = `
        @{
          computerName = $env:COMPUTERNAME
          userName = $env:USERNAME
          osVersion = (Get-WmiObject -Class Win32_OperatingSystem).Version
          totalMemory = (Get-WmiObject -Class Win32_ComputerSystem).TotalPhysicalMemory / 1GB
        } | ConvertTo-Json
      `;

      return await this.executePowerShell(psCommand);
    } catch (error) {
      console.error('Failed to get system info:', error);
      return null;
    }
  }

  /**
   * Execute PowerShell command via Tauri
   */
  async executePowerShell(command) {
    try {
      // This requires Tauri shell plugin to be configured in tauri.conf.json
      // See: https://tauri.app/docs/features/shell/

      const result = await invokeTauri('run_shell_command', {
        cmd: 'powershell.exe',
        args: ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command]
      });

      if (!result || !String(result).trim()) return null;
      return JSON.parse(result);
    } catch (error) {
      console.error('PowerShell execution error:', error);
      return null;
    }
  }

  /**
   * Monitor idle time
   */
  async getIdleTime() {
    try {
      const psCommand = `
        $lastInput = (Get-EventLog -LogName Security -InstanceId 4624 -Newest 1 -ErrorAction SilentlyContinue).TimeGenerated
        if ($lastInput) {
          ((Get-Date) - $lastInput).TotalSeconds
        } else {
          0
        }
      `;

      const idleSeconds = await this.executePowerShell(psCommand);
      return idleSeconds || 0;
    } catch (error) {
      return 0;
    }
  }

  /**
   * Get system recommendation context
   */
  async getContextForRecommendation() {
    const idleTime = await this.getIdleTime();
    const activeApps = Array.from(this.trackedApps);
    const systemInfo = await this.getSystemInfo();

    return {
      idleTimeSeconds: idleTime,
      activeApplications: activeApps.slice(0, 5), // Last 5 apps
      systemInfo,
      timestamp: new Date().toISOString()
    };
  }

  async getFullContext() {
    const [activeWindows, installedApps, battery, systemInfo] = await Promise.all([
      this.getActiveWindows(),
      this.getInstalledApps(),
      this.checkBatteryStatus(),
      this.getSystemInfo()
    ]);

    const comms = await this.getCommunicationSignals(activeWindows);
    const games = await this.getGamesAndLaunchers(activeWindows, installedApps);

    activeWindows.forEach(w => this.trackedApps.add(`${w.processName}:${w.windowTitle}`));

    this.currentContext = {
      activeWindows,
      installedApps,
      comms,
      games,
      battery,
      systemInfo,
      permissionNote: 'Contexto local visible y autorizado por el usuario.',
      lastUpdated: new Date().toISOString()
    };

    return this.currentContext;
  }
}

// Export
if (typeof module !== 'undefined' && module.exports) {
  module.exports = WindowsSystemTracker;
}
