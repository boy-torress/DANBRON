#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::{
    Manager,
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    menu::{Menu, MenuItem},
};
use std::process::Command;
use std::{
    collections::HashSet,
    env,
    fs,
    path::PathBuf,
    thread,
    time::Duration,
};
use serde::Deserialize;
use serde_json::{json, Value};

#[tauri::command]
fn window_minimize(window: tauri::Window) -> Result<(), String> {
    window.minimize().map_err(|err| err.to_string())
}

#[tauri::command]
fn window_hide(window: tauri::Window) -> Result<(), String> {
    window.hide().map_err(|err| err.to_string())
}

#[tauri::command]
fn window_set_always_on_top(window: tauri::Window, pinned: bool) -> Result<(), String> {
    window
        .set_always_on_top(pinned)
        .map_err(|err| err.to_string())
}

#[tauri::command]
fn get_backend_url() -> String {
    let value = config_value(&["DANBRON_BACKEND_URL", "BACKEND_URL", "danbron.backendUrl"])
        .unwrap_or_else(|| "https://danbron-production.up.railway.app/api".to_string());
    let cleaned = value.trim().trim_end_matches('/').to_string();
    if cleaned.ends_with("/api") {
        cleaned
    } else {
        format!("{}/api", cleaned)
    }
}

fn decode_command_output(bytes: &[u8]) -> String {
    if bytes.len() >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE {
        let u16s: Vec<u16> = bytes[2..]
            .chunks_exact(2)
            .map(|chunk| u16::from_le_bytes([chunk[0], chunk[1]]))
            .collect();
        return String::from_utf16_lossy(&u16s).trim().to_string();
    }

    if bytes.len() >= 2 && bytes.len() % 2 == 0 && bytes[0] != 0 && bytes[1] == 0 {
        let u16s: Vec<u16> = bytes
            .chunks_exact(2)
            .map(|chunk| u16::from_le_bytes([chunk[0], chunk[1]]))
            .collect();
        return String::from_utf16_lossy(&u16s).trim().to_string();
    }

    String::from_utf8_lossy(bytes).trim().to_string()
}

#[tauri::command]
fn run_shell_command(cmd: String, args: Vec<String>) -> Result<String, String> {
    let shell = cmd.to_lowercase();
    if shell != "powershell" && shell != "powershell.exe" && shell != "pwsh" && shell != "pwsh.exe" {
        return Err("Only PowerShell commands are allowed".to_string());
    }

    let output = Command::new(cmd)
        .args(args)
        .output()
        .map_err(|err| err.to_string())?;

    if !output.status.success() {
        return Err(decode_command_output(&output.stderr));
    }

    Ok(decode_command_output(&output.stdout))
}

fn read_local_config_value(key: &str) -> Option<String> {
    let mut candidates: Vec<PathBuf> = Vec::new();

    if let Ok(home) = env::var("USERPROFILE") {
        candidates.push(PathBuf::from(&home).join("Desktop").join("danbron").join("local.properties"));
        candidates.push(PathBuf::from(&home).join("Desktop").join("danbron").join("backend").join(".env"));
    }

    if let Ok(cwd) = env::current_dir() {
        candidates.push(cwd.join("local.properties"));
        candidates.push(cwd.join("backend").join(".env"));
    }

    for path in candidates {
        let Ok(contents) = fs::read_to_string(path) else {
            continue;
        };
        for line in contents.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with('#') || trimmed.is_empty() {
                continue;
            }
            if let Some((name, value)) = trimmed.split_once('=') {
                if name.trim() == key {
                    let cleaned = value.trim().trim_matches('"').trim_matches('\'').to_string();
                    if !cleaned.is_empty() {
                        return Some(cleaned);
                    }
                }
            }
        }
    }

    None
}

fn config_value(keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Ok(value) = env::var(key) {
            if !value.trim().is_empty() {
                return Some(value);
            }
        }
        if let Some(value) = read_local_config_value(key) {
            return Some(value);
        }
    }
    None
}

#[tauri::command]
fn proxy_ai_request(messages_json: String) -> Result<String, String> {
    let provider = config_value(&["AI_PROVIDER"])
        .unwrap_or_else(|| {
            if config_value(&["OPENROUTER_API_KEY", "danbron.defaultApiKey"]).is_some() {
                "openrouter".to_string()
            } else {
                "groq".to_string()
            }
        });

    let is_openrouter = provider.eq_ignore_ascii_case("openrouter");
    let api_key = if is_openrouter {
        config_value(&["OPENROUTER_API_KEY", "danbron.defaultApiKey"])
            .ok_or_else(|| "OPENROUTER_API_KEY missing".to_string())?
    } else {
        config_value(&["GROQ_API_KEY"])
            .ok_or_else(|| "GROQ_API_KEY missing".to_string())?
    };

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(45))
        .build()
        .map_err(|e| format!("Client build error: {}", e))?;

    let messages: serde_json::Value = serde_json::from_str(&messages_json)
        .map_err(|e| format!("JSON parse error: {}", e))?;

    let endpoint = if is_openrouter {
        "https://openrouter.ai/api/v1/chat/completions"
    } else {
        "https://api.groq.com/openai/v1/chat/completions"
    };

    let site_url = config_value(&["OPENROUTER_SITE_URL"]).unwrap_or_else(|| "https://danbron.local".to_string());
    let app_name = config_value(&["OPENROUTER_APP_NAME"]).unwrap_or_else(|| "Danbron".to_string());

    // Model fallback chain: best free models first
    let user_model = config_value(&["OPENROUTER_MODEL", "AI_MODEL_OPENROUTER"]);
    let models: Vec<String> = if let Some(ref m) = user_model {
        vec![m.clone()]
    } else if is_openrouter {
        vec![
            "openai/gpt-oss-120b:free".to_string(),
            "nvidia/nemotron-3-super-120b-a12b:free".to_string(),
            "minimax/minimax-m2.5:free".to_string(),
            "nvidia/nemotron-nano-12b-v2-vl:free".to_string(),
            "meta-llama/llama-3.3-70b-instruct:free".to_string(),
            "google/gemma-4-31b-it:free".to_string(),
            "nousresearch/hermes-3-llama-3.1-405b:free".to_string(),
        ]
    } else {
        vec![config_value(&["GROQ_MODEL", "AI_MODEL_GROQ"])
            .unwrap_or_else(|| "llama-3.3-70b-versatile".to_string())]
    };

    let mut last_error = String::from("All models failed");

    for model in &models {
        let body = serde_json::json!({
            "model": model,
            "max_tokens": 1500,
            "temperature": 0.3,
            "messages": messages
        });

        let mut request = client
            .post(endpoint)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&body);

        if is_openrouter {
            request = request
                .header("HTTP-Referer", &site_url)
                .header("X-Title", &app_name);
        }

        match request.send() {
            Ok(response) => {
                if response.status().is_success() {
                    if let Ok(data) = response.json::<serde_json::Value>() {
                        let content = data["choices"][0]["message"]["content"]
                            .as_str()
                            .unwrap_or("")
                            .to_string();
                        if !content.is_empty() {
                            return Ok(content);
                        }
                    }
                } else {
                    let status = response.status().as_u16();
                    let body_text = response.text().unwrap_or_default();
                    last_error = format!("{} error {}: {}", model, status, &body_text[..body_text.len().min(200)]);
                    // 401/403 = bad key, don't retry other models
                    if status == 401 || status == 403 {
                        return Err(last_error);
                    }
                    // 429 or 5xx = rate limited or down, try next model
                    continue;
                }
            }
            Err(e) => {
                last_error = format!("{} request failed: {}", model, e);
                continue;
            }
        }
    }

    Err(last_error)
}

/// Deep Thinking (Mixture-of-Agents): call 3 models in parallel, synthesize the best answer
#[tauri::command]
fn proxy_ai_deep_request(messages_json: String) -> Result<String, String> {
    let api_key = config_value(&["OPENROUTER_API_KEY", "danbron.defaultApiKey"])
        .ok_or_else(|| "OPENROUTER_API_KEY missing".to_string())?;

    let messages: serde_json::Value = serde_json::from_str(&messages_json)
        .map_err(|e| format!("JSON parse error: {}", e))?;

    let site_url = config_value(&["OPENROUTER_SITE_URL"]).unwrap_or_else(|| "https://danbron.local".to_string());

    // 3 diverse proposer models for maximum perspective diversity
    let proposers = vec![
        "openai/gpt-oss-120b:free",
        "deepseek/deepseek-v4-flash:free",
        "meta-llama/llama-3.3-70b-instruct:free",
    ];

    // Aggregator: strong model that synthesizes
    let aggregator_models = vec![
        "nvidia/nemotron-3-super-120b-a12b:free",
        "openai/gpt-oss-120b:free",
        "minimax/minimax-m2.5:free",
    ];

    // Phase 1: Call proposers in parallel using threads
    let mut handles = Vec::new();
    for model in &proposers {
        let model_name = model.to_string();
        let key = api_key.clone();
        let msgs = messages.clone();
        let url = site_url.clone();

        let handle = thread::spawn(move || {
            let client = reqwest::blocking::Client::builder()
                .timeout(Duration::from_secs(40))
                .build()
                .ok()?;

            let body = json!({
                "model": model_name,
                "max_tokens": 1200,
                "temperature": 0.7,
                "messages": msgs
            });

            let resp = client
                .post("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", format!("Bearer {}", key))
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", &url)
                .header("X-Title", "Danbron")
                .json(&body)
                .send()
                .ok()?;

            if resp.status().is_success() {
                let data: serde_json::Value = resp.json().ok()?;
                let content = data["choices"][0]["message"]["content"].as_str()?.to_string();
                if !content.is_empty() {
                    return Some((model_name, content));
                }
            }
            None
        });
        handles.push(handle);
    }

    // Collect proposer responses
    let mut proposals: Vec<(String, String)> = Vec::new();
    for handle in handles {
        if let Ok(Some(result)) = handle.join() {
            proposals.push(result);
        }
    }

    // If zero proposals succeeded, fall back to normal single-model call
    if proposals.is_empty() {
        return proxy_ai_request(messages_json);
    }

    // If only 1 proposal, return it directly (no point in aggregating)
    if proposals.len() == 1 {
        return Ok(proposals[0].1.clone());
    }

    // Phase 2: Aggregate — synthesize the best answer from all proposals
    let mut synthesis_parts = String::new();
    for (i, (model, response)) in proposals.iter().enumerate() {
        synthesis_parts.push_str(&format!(
            "\n--- EXPERTO {} ({}) ---\n{}\n",
            i + 1, model.split('/').last().unwrap_or(model), response
        ));
    }

    let synthesis_prompt = format!(
        "Eres un sintetizador experto. Abajo hay {} respuestas de distintos expertos a la misma pregunta del usuario. \
Tu trabajo es crear UNA SOLA respuesta final que:\n\
1. Tome lo MEJOR de cada experto (datos correctos, ideas utiles, perspectivas unicas)\n\
2. Corrija errores o contradicciones entre ellos\n\
3. Sea clara, concisa y en español latino casual\n\
4. NO menciones que hay multiples expertos. Responde como si fueras TU, Bron, el amigo del usuario\n\
5. Mantén el tono natural y amigable\n\n\
RESPUESTAS DE LOS EXPERTOS:{}\n\n\
RESPUESTA FINAL SINTETIZADA:",
        proposals.len(), synthesis_parts
    );

    // Build aggregation messages: keep original system prompt, add synthesis as user message
    let original_system = if messages.is_array() {
        messages.as_array().and_then(|arr| arr.first())
            .and_then(|m| m["content"].as_str())
            .unwrap_or("Eres Bron, amigo personal del usuario.")
            .to_string()
    } else {
        "Eres Bron, amigo personal del usuario.".to_string()
    };

    // Extract original user question for context
    let user_question = if messages.is_array() {
        messages.as_array().and_then(|arr| arr.last())
            .and_then(|m| m["content"].as_str())
            .unwrap_or("")
            .to_string()
    } else {
        String::new()
    };

    let agg_messages = json!([
        { "role": "system", "content": original_system },
        { "role": "user", "content": user_question },
        { "role": "user", "content": synthesis_prompt }
    ]);

    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(45))
        .build()
        .map_err(|e| format!("Client build error: {}", e))?;

    // Try aggregator models
    for model in &aggregator_models {
        let body = json!({
            "model": model,
            "max_tokens": 1500,
            "temperature": 0.2,
            "messages": agg_messages
        });

        match client
            .post("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", &site_url)
            .header("X-Title", "Danbron")
            .json(&body)
            .send()
        {
            Ok(response) => {
                let status = response.status();
                if status.is_success() {
                    if let Ok(data) = response.json::<serde_json::Value>() {
                        let content = data["choices"][0]["message"]["content"]
                            .as_str()
                            .unwrap_or("")
                            .to_string();
                        if !content.is_empty() {
                            return Ok(content);
                        }
                    }
                } else {
                    let code = status.as_u16();
                    if code == 401 || code == 403 {
                        break;
                    }
                }
                continue;
            }
            Err(_) => continue,
        }
    }

    // Aggregation failed — return best single proposal (longest, usually most complete)
    let best = proposals.iter().max_by_key(|(_, r)| r.len()).unwrap();
    Ok(best.1.clone())
}

/// Vision AI: analyze an image (base64) with a vision-capable model
#[tauri::command]
fn proxy_ai_vision(image_base64: String, prompt: String) -> Result<String, String> {
    let api_key = config_value(&["OPENROUTER_API_KEY", "danbron.defaultApiKey"])
        .ok_or_else(|| "OPENROUTER_API_KEY missing for vision".to_string())?;

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(60))
        .build()
        .map_err(|e| format!("Client build error: {}", e))?;

    // Vision-capable models on OpenRouter — broad fallback chain
    // First try the same model the user has configured for chat (if any)
    let user_model = config_value(&["OPENROUTER_MODEL", "AI_MODEL_OPENROUTER"]);
    let mut vision_models: Vec<String> = Vec::new();
    if let Some(ref m) = user_model {
        vision_models.push(m.clone());
    }
    // Free vision-capable models (verified working on OpenRouter)
    vision_models.extend(vec![
        "nvidia/nemotron-nano-12b-v2-vl:free".to_string(),
        "openai/gpt-oss-120b:free".to_string(),
        "nvidia/nemotron-3-super-120b-a12b:free".to_string(),
        "minimax/minimax-m2.5:free".to_string(),
        "meta-llama/llama-3.3-70b-instruct:free".to_string(),
        "google/gemma-4-31b-it:free".to_string(),
    ]);

    let data_url = if image_base64.starts_with("data:") {
        image_base64.clone()
    } else {
        format!("data:image/png;base64,{}", image_base64)
    };

    let user_prompt = if prompt.is_empty() {
        "Describe detalladamente lo que ves en esta imagen. Si hay una persona, describe su apariencia, que lleva puesto (lentes, ropa, accesorios), su expresion. Describe tambien el fondo y ambiente. Responde en español.".to_string()
    } else {
        prompt
    };

    let messages = serde_json::json!([
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": user_prompt
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": data_url
                    }
                }
            ]
        }
    ]);

    let site_url = config_value(&["OPENROUTER_SITE_URL"]).unwrap_or_else(|| "https://danbron.local".to_string());
    let mut last_error = String::from("All vision models failed");

    for model in &vision_models {
        let body = serde_json::json!({
            "model": model,
            "max_tokens": 1000,
            "temperature": 0.3,
            "messages": messages
        });

        match client
            .post("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", &site_url)
            .header("X-Title", "Danbron")
            .json(&body)
            .send()
        {
            Ok(response) => {
                if response.status().is_success() {
                    if let Ok(data) = response.json::<serde_json::Value>() {
                        let content = data["choices"][0]["message"]["content"]
                            .as_str()
                            .unwrap_or("")
                            .to_string();
                        if !content.is_empty() {
                            return Ok(content);
                        }
                    }
                } else {
                    let status = response.status().as_u16();
                    let body_text = response.text().unwrap_or_default();
                    last_error = format!("{} vision error {}: {}", model, status, &body_text[..body_text.len().min(200)]);
                    eprintln!("[VISION] {} → {}", model, last_error);
                    if status == 401 || status == 403 { return Err(last_error); }
                    continue;
                }
            }
            Err(e) => {
                last_error = format!("{} vision request failed: {}", model, e);
                continue;
            }
        }
    }

    Err(last_error)
}

#[derive(Debug, Deserialize)]
struct DiskSession {
    token: String,
    #[serde(rename = "deviceId")]
    device_id: String,
}

#[derive(Debug, Deserialize)]
struct EventsResponse {
    events: Vec<DeviceEvent>,
}

#[derive(Debug, Deserialize)]
struct DeviceEvent {
    event_type: String,
    event_data: Option<Value>,
}

fn session_path() -> Option<std::path::PathBuf> {
    std::env::var("LOCALAPPDATA")
        .ok()
        .map(|base| std::path::Path::new(&base).join("Danbron").join("session.json"))
}

fn processed_commands_path() -> Option<std::path::PathBuf> {
    std::env::var("LOCALAPPDATA")
        .ok()
        .map(|base| std::path::Path::new(&base).join("Danbron").join("processed_commands.json"))
}

fn append_command_log(message: &str) {
    if let Ok(base) = std::env::var("LOCALAPPDATA") {
        let dir = std::path::Path::new(&base).join("Danbron");
        let _ = fs::create_dir_all(&dir);
        let path = dir.join("command.log");
        let line = format!("{} {}\n", iso_timestamp(), message);
        let _ = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .and_then(|mut file| {
                use std::io::Write;
                file.write_all(line.as_bytes())
            });
    }
}

fn read_disk_session() -> Option<DiskSession> {
    let path = session_path()?;
    let raw = fs::read_to_string(path).ok()?;
    let clean = raw.trim_start_matches('\u{feff}');
    serde_json::from_str(clean).ok()
}

fn load_processed_commands() -> HashSet<String> {
    let Some(path) = processed_commands_path() else {
        return HashSet::new();
    };
    let Ok(raw) = fs::read_to_string(path) else {
        return HashSet::new();
    };
    let clean = raw.trim_start_matches('\u{feff}');
    serde_json::from_str::<Vec<String>>(clean)
        .unwrap_or_default()
        .into_iter()
        .collect()
}

fn save_processed_commands(processed: &HashSet<String>) {
    let Some(path) = processed_commands_path() else {
        return;
    };
    if let Some(dir) = path.parent() {
        let _ = fs::create_dir_all(dir);
    }
    let mut ids: Vec<String> = processed.iter().cloned().collect();
    ids.sort();
    if ids.len() > 500 {
        ids = ids.split_off(ids.len() - 500);
    }
    if let Ok(body) = serde_json::to_string(&ids) {
        let _ = fs::write(path, body);
    }
}

fn command_id_from_event(event: &DeviceEvent) -> Option<String> {
    if event.event_type != "remote_command" {
        return None;
    }
    event
        .event_data
        .as_ref()?
        .get("id")?
        .as_str()
        .map(|id| id.to_string())
}

fn ps_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', "''"))
}

fn command_script(command: &Value) -> Option<String> {
    let action = command.get("action")?.as_str()?;
    let args = command.get("args").cloned().unwrap_or_else(|| json!({}));

    match action {
        "open_url" => {
            let url = args.get("url")?.as_str()?;
            Some(format!("Start-Process {}", ps_quote(url)))
        }
        "open_app" => {
            let app = args.get("app")?.as_str()?.to_lowercase();
            match app.as_str() {
                "opera" => Some([
                    "$paths = @(\"$env:LOCALAPPDATA\\Programs\\Opera\\launcher.exe\", \"$env:ProgramFiles\\Opera\\launcher.exe\", \"${env:ProgramFiles(x86)}\\Opera\\launcher.exe\")",
                    "$exe = $paths | Where-Object { Test-Path $_ } | Select-Object -First 1",
                    "if ($exe) { Start-Process $exe } else { Start-Process \"opera\" }",
                ].join("; ")),
                "chrome" => Some("Start-Process \"chrome\"".to_string()),
                "whatsapp" | "wsp" => Some("Start-Process \"whatsapp:\"".to_string()),
                "gmail" => Some("Start-Process \"https://mail.google.com/\"".to_string()),
                "calendar" | "calendario" => Some("Start-Process \"https://calendar.google.com/\"".to_string()),
                "drive" => Some("Start-Process \"https://drive.google.com/\"".to_string()),
                "docs" | "documentos" => Some("Start-Process \"https://docs.google.com/document/u/0/\"".to_string()),
                "sheets" | "hojas" => Some("Start-Process \"https://docs.google.com/spreadsheets/u/0/\"".to_string()),
                "contacts" | "contactos" => Some("Start-Process \"https://contacts.google.com/\"".to_string()),
                "spotify" => Some("Start-Process \"spotify:\"".to_string()),
                "music" | "musica" => Some("Start-Process \"https://music.youtube.com/\"".to_string()),
                "youtube" => Some("Start-Process \"https://www.youtube.com/\"".to_string()),
                _ => None,
            }
        }
        "compose_whatsapp" => {
            let message = args.get("message").and_then(|v| v.as_str()).unwrap_or("");
            Some(format!(
                "Start-Process \"https://wa.me/?text={}\"",
                url_encode(message)
            ))
        }
        "compose_email" => {
            let to = args.get("to").and_then(|v| v.as_str()).unwrap_or("");
            let subject = args.get("subject").and_then(|v| v.as_str()).unwrap_or("");
            let body = args.get("body").and_then(|v| v.as_str()).unwrap_or("");
            Some(format!(
                "Start-Process \"https://mail.google.com/mail/?view=cm&fs=1&to={}&su={}&body={}\"",
                url_encode(to),
                url_encode(subject),
                url_encode(body)
            ))
        }
        "send_email" => {
            let to = args.get("to").and_then(|v| v.as_str()).unwrap_or("");
            let subject = args.get("subject").and_then(|v| v.as_str()).unwrap_or("");
            let body = args.get("body").and_then(|v| v.as_str()).unwrap_or("");
            Some([
                "$ErrorActionPreference = \"Stop\"".to_string(),
                format!("$to = {}", ps_quote(to)),
                format!("$subject = {}", ps_quote(subject)),
                format!("$body = {}", ps_quote(body)),
                "if (Get-Command gog -ErrorAction SilentlyContinue) {".to_string(),
                "  gog gmail send --to $to --subject $subject --body $body --force --no-input".to_string(),
                "} elseif (Get-Command maton -ErrorAction SilentlyContinue) {".to_string(),
                "  maton google-mail message send --to $to --subject $subject --body $body".to_string(),
                "} else {".to_string(),
                format!(
                    "  Start-Process \"https://mail.google.com/mail/?view=cm&fs=1&to={}&su={}&body={}\"",
                    url_encode(to),
                    url_encode(subject),
                    url_encode(body)
                ),
                "}".to_string(),
            ].join("; "))
        }
        "create_calendar_event" => {
            let title = args.get("title").and_then(|v| v.as_str()).unwrap_or("Nuevo evento");
            let details = args.get("details").and_then(|v| v.as_str()).unwrap_or("");
            Some(format!(
                "Start-Process \"https://calendar.google.com/calendar/render?action=TEMPLATE&text={}&details={}&ctz=America%2FSantiago\"",
                url_encode(title),
                url_encode(details)
            ))
        }
        "play_music" => {
            let query = args.get("query").and_then(|v| v.as_str()).unwrap_or("");
            let service = args.get("service").and_then(|v| v.as_str()).unwrap_or("auto");
            if service == "spotify" {
                if query.is_empty() {
                    Some("Start-Process \"spotify:\"".to_string())
                } else {
                    Some(format!("Start-Process \"spotify:search:{}\"", url_encode(query)))
                }
            } else if query.is_empty() {
                Some("Start-Process \"https://music.youtube.com/\"".to_string())
            } else {
                Some(format!("Start-Process \"https://music.youtube.com/search?q={}\"", url_encode(query)))
            }
        }
        "open_maps" => {
            let query = args.get("query").and_then(|v| v.as_str()).unwrap_or("");
            Some(format!(
                "Start-Process \"https://www.google.com/maps/search/?api=1&query={}\"",
                url_encode(query)
            ))
        }
        _ => None,
    }
}

fn url_encode(value: &str) -> String {
    value
        .bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            b' ' => "%20".to_string(),
            _ => format!("%{:02X}", b),
        })
        .collect()
}

fn execute_native_command(command: &Value) -> Result<String, String> {
    let script = command_script(command).ok_or_else(|| "Accion no soportada en Windows".to_string())?;
    let output = Command::new("powershell.exe")
        .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", &script])
        .output()
        .map_err(|err| err.to_string())?;

    if !output.status.success() {
        Err(decode_command_output(&output.stderr))
    } else {
        Ok("Comando ejecutado en Windows".to_string())
    }
}

fn post_command_result(
    client: &reqwest::blocking::Client,
    session: &DiskSession,
    command_id: &str,
    status: &str,
    result: Value,
) {
    let send_result = client
        .post("https://danbron-production.up.railway.app/api/sync/event")
        .bearer_auth(&session.token)
        .json(&json!({
            "deviceId": session.device_id,
            "eventType": "remote_command_result",
            "eventData": {
                "commandId": command_id,
                "status": status,
                "result": result,
                "completedAt": iso_timestamp()
            }
        }))
        .send();

    if let Err(error) = send_result {
        append_command_log(&format!(
            "failed to post result for command {}: {}",
            command_id, error
        ));
    }
}

fn iso_timestamp() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};

    let duration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let total_secs = duration.as_secs();
    let millis = duration.subsec_millis();

    let hour = (total_secs / 3600) % 24;
    let minute = (total_secs / 60) % 60;
    let second = total_secs % 60;

    let z = (total_secs / 86400) as i64 + 719_468;
    let era = (if z >= 0 { z } else { z - 146_096 }) / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = doy - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = if month <= 2 { y + 1 } else { y };

    format!(
        "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}.{:03}Z",
        year, month, day, hour, minute, second, millis
    )
}

fn start_native_command_polling() {
    thread::spawn(|| {
        append_command_log("native command poller started");
        let client = reqwest::blocking::Client::new();
        let mut processed = load_processed_commands();
        let mut bootstrapped_existing_commands = false;

        loop {
            if let Some(session) = read_disk_session() {
                let url = format!(
                    "https://danbron-production.up.railway.app/api/sync/events?deviceId={}&limit=100",
                    session.device_id
                );

                match client.get(url).bearer_auth(&session.token).send() {
                    Ok(response) => {
                    match response.json::<EventsResponse>() {
                    Ok(events) => {
                        if !bootstrapped_existing_commands {
                            let mut added = 0;
                            for event in &events.events {
                                if let Some(command_id) = command_id_from_event(event) {
                                    if processed.insert(command_id) {
                                        added += 1;
                                    }
                                }
                            }
                            save_processed_commands(&processed);
                            bootstrapped_existing_commands = true;
                            append_command_log(&format!(
                                "bootstrapped {} existing remote commands without executing them",
                                added
                            ));
                            continue;
                        }

                        for event in events.events {
                            if event.event_type != "remote_command" {
                                continue;
                            }
                            let Some(data) = event.event_data else { continue };
                            let Some(command_id) = data.get("id").and_then(|v| v.as_str()) else { continue };
                            if processed.contains(command_id) {
                                continue;
                            }
                            let target = data.get("target").and_then(|v| v.as_str()).unwrap_or("windows");
                            if !["windows", "pc", "desktop", "paired"].contains(&target) {
                                continue;
                            }

                            processed.insert(command_id.to_string());
                            save_processed_commands(&processed);
                            append_command_log(&format!("executing command {}", command_id));
                            let result = execute_native_command(&data);
                            match result {
                                Ok(message) => {
                                    append_command_log(&format!("completed command {}", command_id));
                                    post_command_result(
                                        &client,
                                        &session,
                                        command_id,
                                        "completed",
                                        json!({ "message": message }),
                                    )
                                },
                                Err(error) => {
                                    append_command_log(&format!("failed command {}: {}", command_id, error));
                                    post_command_result(
                                        &client,
                                        &session,
                                        command_id,
                                        "failed",
                                        json!({ "error": error }),
                                    )
                                },
                            }
                        }
                    }
                    Err(error) => append_command_log(&format!("json parse failed: {}", error)),
                    }
                    }
                    Err(error) => append_command_log(&format!("backend poll failed: {}", error)),
                }
            } else {
                append_command_log("no disk session");
            }

            thread::sleep(Duration::from_secs(7));
        }
    });
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            run_shell_command,
            proxy_ai_request,
            proxy_ai_deep_request,
            proxy_ai_vision,
            get_backend_url,
            window_minimize,
            window_hide,
            window_set_always_on_top
        ])
        .setup(|app| {
            start_native_command_polling();
            // Build tray menu
            let show_item = MenuItem::with_id(app, "show", "Abrir Danbron", true, None::<&str>)?;
            let quit_item = MenuItem::with_id(app, "quit", "Cerrar", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show_item, &quit_item])?;

            // Create tray icon
            let _tray = TrayIconBuilder::new()
                .menu(&menu)
                .tooltip("Danbron - Bron esta aqui para ti")
                .on_menu_event(move |app, event| {
                    match event.id.as_ref() {
                        "show" => {
                            if let Some(window) = app.get_webview_window("main") {
                                let _ = window.show();
                                let _ = window.set_focus();
                            }
                        }
                        "quit" => {
                            app.exit(0);
                        }
                        _ => {}
                    }
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                })
                .build(app)?;

            Ok(())
        })
        .on_window_event(|window, event| {
            // Minimize to tray on close
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running Danbron Desktop");
}
