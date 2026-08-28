import { createContext, useContext, useState } from "react";
import { api } from "./api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => {
    const token = localStorage.getItem("token");
    const email = localStorage.getItem("email");
    const role = localStorage.getItem("role");
    return token ? { token, email, role } : null;
  });

  function persist(data) {
    localStorage.setItem("token", data.token);
    localStorage.setItem("email", data.email);
    localStorage.setItem("role", data.role);
    setAuth({ token: data.token, email: data.email, role: data.role });
  }

  async function login(email, password) {
    persist(await api.login(email, password));
  }

  async function register(email, password) {
    persist(await api.register(email, password));
  }

  function logout() {
    // Only the auth keys: localStorage.clear() would also drop the cart.
    localStorage.removeItem("token");
    localStorage.removeItem("email");
    localStorage.removeItem("role");
    setAuth(null);
  }

  const value = {
    auth,
    login,
    register,
    logout,
    isLoggedIn: auth !== null,
    isAdmin: auth?.role === "ADMIN",
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}