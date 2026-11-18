import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL
const anonKey =
  import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || import.meta.env.VITE_SUPABASE_ANON_KEY

export const isConfigured = Boolean(
  url && anonKey && !url.includes('xxxx') && !anonKey.includes('your_')
)

export const supabase = createClient(
  url || 'https://placeholder.supabase.co',
  anonKey || 'placeholder-anon-key',
  {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: false
    }
  }
)

export const getSession = () => supabase.auth.getSession()

export const signOut = async () => {
  try {
    // 只清本地会话，不依赖网络请求，避免网络异常时无法退出
    await supabase.auth.signOut({ scope: 'local' })
  } finally {
    // 兜底：清理 supabase-js 写入的会话键，防止残留会话把用户弹回首页
    Object.keys(localStorage)
      .filter((key) => key.startsWith('sb-') && key.includes('-auth-token'))
      .forEach((key) => localStorage.removeItem(key))
  }
}
