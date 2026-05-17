# Danbron Sync System - Configuración Rápida

## Paso 1: Supabase (Gratis)

1. Ir a https://supabase.com
2. Crear proyecto gratuito
3. Ir a SQL Editor
4. Copiar-pegar contenido de `backend/schema.sql`
5. Ejecutar query
6. Ir a Settings → API:
   - Copiar **Project URL** y **anon public key**
   - Ir a JWT Settings, copiar **JWT Secret**

## Paso 2: Backend

```bash
cd backend
npm install

# Crear archivo .env
cp .env.example .env
```

Editar `.env`:
```env
SUPABASE_URL=https://xxx.supabase.co
SUPABASE_KEY=ey...
SUPABASE_JWT_SECRET=super-secret-xxx
JWT_SECRET=your-secret-key
PORT=3000
```

Iniciar:
```bash
npm start
# Servidor en http://localhost:3000
```

Deplegar (Railway):
```bash
npm install -g @railway/cli
railway up
# Seguir instrucciones, copiar URL pública
```

## Paso 3: Desktop

1. Editar `desktop/src/sync-client.js`:
   ```javascript
   const BACKEND_URL = "https://tu-backend.com/api";
   ```

2. Instalar dependencias:
   ```bash
   cd desktop
   npm install
   ```

3. Probar localmente:
   ```bash
   npm run dev
   ```

4. Build para producción:
   ```bash
   cargo tauri build
   ```

## Paso 4: Android

1. Editar `app/build.gradle.kts`:
   ```kotlin
   buildTypes {
       debug {
           buildConfigField "String", "BACKEND_URL", 
               "\"https://tu-backend.com/api/\""
       }
       release {
           buildConfigField "String", "BACKEND_URL", 
               "\"https://tu-backend.com/api/\""
       }
   }
   ```

2. Build y run:
   ```bash
   cd app
   ./gradlew installDebug
   ```

## Paso 5: Probar Pairing

### Desktop:
1. Abrir app
2. Ingresar email (ej: test@example.com)
3. Click "Conectar"
4. Se abre diálogo de pairing
5. Copiar código (6 dígitos)

### Android:
1. Abrir app
2. Ingresar mismo email
3. Ver código en pantalla
4. Click "Emparejar"
5. Ingresar código de Desktop

### Verificar:
- ✅ Desktop dice "Emparejado"
- ✅ Android dice "Emparejado"
- ✅ Datos se sincronizan en tiempo real

## Variables de Entorno

| Variable | Dónde Obtener | Ejemplo |
|----------|---------------|---------|
| `SUPABASE_URL` | Supabase → Settings → API | `https://abc.supabase.co` |
| `SUPABASE_KEY` | Supabase → Settings → API (anon key) | `eyJhbGc...` |
| `SUPABASE_JWT_SECRET` | Supabase → Settings → JWT | `super-secret-xxx` |
| `JWT_SECRET` | Cualquier string aleatorio | `my-secret-key-123` |
| `BACKEND_URL` | Tu URL de Railway/Render | `https://danbron-backend.railway.app/api` |
| `AI_PROVIDER` | groq o anthropic | `groq` |
| `GROQ_API_KEY` | Groq API key | `gsk_...` |
| `ANTHROPIC_API_KEY` | Anthropic API key | `sk-ant-...` |

## Problemas Comunes

### "Invalid token"
- Verificar `JWT_SECRET` es igual en backend y cliente
- Check JWT no expiró (válido 30 días)

### "Device not registered"
- Hacer registro antes de pairing
- Verificar email es igual en ambos dispositivos

### "Pairing code expired"
- Código válido solo 5 minutos
- Generar código nuevo en Settings

### No sincroniza datos
- Verificar `device_pairs` en Supabase
- Check `sync_data` table
- Ver logs del backend

### System events no se registran (Android)
- Habilitar Accessibility Service en Settings
- Dar permisos de contactos/calendario
- Verificar `device_events` table

---

## Próximos Pasos

1. Personalizar Bron (prompt del sistema)
2. Agregar más eventos del sistema
3. Crear dashboard de análisis
4. Implementar OAuth
5. Agregar soporte para múltiples idiomas
6. Implementar backup en nube

---

¡Listo! El sistema está funcionando. Ahora Bron tiene acceso completo a ambos dispositivos y puede dar recomendaciones contextuales.
