const BASE_URL = "http://localhost:8080/api";

function authHeaders() {
  const token = localStorage.getItem("token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
      ...options.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const problem = await response.json();
      message = problem.detail || problem.message || message;
      if (problem.errors) {
        message = Object.entries(problem.errors)
          .map(([field, msg]) => `${field}: ${msg}`)
          .join(", ");
      }
    } catch {
      // no JSON body — keep the generic message
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  register: (email, password) =>
    request("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

  login: (email, password) =>
    request("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

  getItems: () => request("/items"),

  getItem: (id) => request(`/items/${id}`),

  createItem: (item) =>
    request("/admin/items", {
      method: "POST",
      body: JSON.stringify(item),
    }),

  initializeCheckout: (payload) =>
    request("/checkout/initialize", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getOrder: (id) => request(`/orders/${id}`),

  getMyOrders: () => request("/users/orders"),
};