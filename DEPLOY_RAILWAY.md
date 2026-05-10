# INSTRUCCIONES PARA DEPLOYAR BACKEND EN RAILWAY

## PASO 1: Prepara el Backend Localmente

1. Abre una terminal en: `c:\Users\Boy-Torres\Desktop\danbron\backend`
2. Ejecuta:
   ```
   npm install
   ```

## PASO 2: Crea una Cuenta en Railway

1. Ve a: https://railway.app
2. Clickea "Deploy Now" o Sign Up
3. Conecta con GitHub (recomendado) o email

## PASO 3: Deploy en Railway

1. En railway.app, clickea **+ New Project**
2. Selecciona **GitHub Repo** (o **Deploy from GitHub**)
3. Conecta tu cuenta de GitHub
4. Busca el repo `danbron` y selecciona la carpeta `backend`

## PASO 4: Configura Variables de Entorno

1. En Railway, ve a **Variables**
2. Agrega estas variables (con los valores reales):
   ```
   SUPABASE_URL=https://ykafkyoajmqlapamdfsc.supabase.co
   SUPABASE_KEY=TU_ANON_KEY_AQUI
   SUPABASE_SERVICE_ROLE=TU_SERVICE_ROLE_KEY_AQUI
   SUPABASE_JWT_SECRET=TU_JWT_SECRET_AQUI
   PORT=3000
   NODE_ENV=production
   JWT_SECRET=danbron-super-secret-key-2026
   CORS_ORIGIN=*
   ```

## PASO 5: Espera el Deploy

- Railway automáticamente detectará package.json
- Ejecutará `npm install` y luego `npm start`
- En 2-5 minutos estará listo

## PASO 6: Obtén la URL del Backend

- En Railway, ve a **Deployments**
- Copia la URL que dice algo como: `https://danbron-backend-xxxx.railway.app`
- Esa es tu BACKEND_URL para las apps

## ALTERNATIVA: Si no tienes GitHub

1. Descarga el backend como ZIP
2. En Railway, selecciona **Deploy from GitHub** → **New Repo**
3. O usa el CLI de Railway:
   ```
   npm install -g @railway/cli
   railway login
   railway init
   railway up
   ```

---

## ARCHIVOS NECESARIOS PARA EL DEPLOY

El backend ya tiene todo listo:
- ✅ package.json (dependencias)
- ✅ src/index.js (servidor)
- ✅ src/db.js (conexión Supabase)
- ✅ src/middleware/auth.js
- ✅ src/services/deviceService.js
- ✅ src/services/syncService.js
- ✅ src/routes/*.js

Solo necesitas las claves de Supabase.

---

## CLAVES QUE NECESITO DE TI:

De Supabase (Settings → API):
1. **anon public key**: (empieza con eyJ...)
2. **service_role key**: (la más larga)
3. **JWT Secret**: (en Settings → API)

Envíame esas 3 y termino todo! 🚀
