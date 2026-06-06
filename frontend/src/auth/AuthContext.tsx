import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  authApi,
  clearToken,
  getToken,
  setToken,
  setUnauthorizedHandler,
} from '../api/client'

// The username isn't in any token we can read client-side, so we persist it alongside the token
// to keep "Signed in as ..." correct across a page refresh.
const USERNAME_KEY = 'scicalc.username'

interface AuthContextValue {
  token: string | null
  username: string | null
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // Seed from localStorage so a refresh keeps the session.
  const [token, setTokenState] = useState<string | null>(() => getToken())
  const [username, setUsername] = useState<string | null>(() =>
    localStorage.getItem(USERNAME_KEY),
  )

  async function login(user: string, password: string): Promise<void> {
    const response = await authApi.login(user, password)
    setToken(response.token)
    localStorage.setItem(USERNAME_KEY, user)
    setTokenState(response.token)
    setUsername(user)
  }

  async function register(user: string, password: string): Promise<void> {
    // The backend's register is single-purpose: it creates the user and returns 201 with NO
    // token. So after a successful register the client logs in as a second step to obtain the
    // JWT, which lands the user authenticated.
    await authApi.register(user, password)
    await login(user, password)
  }

  function logout(): void {
    clearToken()
    localStorage.removeItem(USERNAME_KEY)
    setTokenState(null)
    setUsername(null)
  }

  // Register logout as the 401 handler once, on mount. logout only closes over stable state
  // setters, so the first render's closure stays correct for the app's lifetime.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ token, username, login, register, logout }),
    // login/register/logout only reference stable setters and module-level helpers.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [token, username],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
