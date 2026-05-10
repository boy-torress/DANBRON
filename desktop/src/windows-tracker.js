/**
 * Windows System Events Tracker
 * Monitors app usage, emails, and system activity
 * Uses Tauri to access Windows APIs and PowerShell commands
 */

const { invoke } = window.__TAURI__.tauri;

class WindowsSystemTracker {
  constructor(syncClient) {
    this.syncClient = syncClient;
    this.trackedApps = new Set();
    this.eventCheckInterval = 60000; // Check every minute
    this.isMonitoring = false;
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
    } catch (error) {
      console.error('Failed to check battery status:', error);
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

      const result = await invoke('run_shell_command', {
        cmd: 'powershell',
        args: ['-Command', command]
      });

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
}

// Export
if (typeof module !== 'undefined' && module.exports) {
  module.exports = WindowsSystemTracker;
}
