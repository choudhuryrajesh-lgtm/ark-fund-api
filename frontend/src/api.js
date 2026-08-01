const BASE = "/api/v1";

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    // The API returns RFC 7807 problem+json on every error — surface its
    // `detail` (or a field error) instead of a generic "request failed".
    const problem = await res.json().catch(() => null);
    const fieldError = problem?.errors && Object.values(problem.errors)[0];
    throw new Error(fieldError || problem?.detail || `Request failed (${res.status})`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  listClients: () => request("/clients?size=100"),
  createClient: (body) => request("/clients", { method: "POST", body: JSON.stringify(body) }),

  listFunds: (clientId) => request(`/clients/${clientId}/funds?size=100`),
  createFund: (clientId, body) =>
    request(`/clients/${clientId}/funds`, { method: "POST", body: JSON.stringify(body) }),

  listInvestors: (clientId) => request(`/clients/${clientId}/investors?size=100`),
  createInvestor: (clientId, body) =>
    request(`/clients/${clientId}/investors`, { method: "POST", body: JSON.stringify(body) }),

  listTransactions: (clientId) =>
    request(`/clients/${clientId}/transactions?size=20&sort=transactionDate,desc`),
  createTransaction: (clientId, body) =>
    request(`/clients/${clientId}/transactions`, { method: "POST", body: JSON.stringify(body) }),

  listTransactionTypes: () => request("/transaction-types"),

  fundReport: (clientId, fundId) => request(`/clients/${clientId}/reports/funds/${fundId}`),
  investorReport: (clientId, investorId) =>
    request(`/clients/${clientId}/reports/investors/${investorId}`),
  portfolioReport: (clientId) => request(`/clients/${clientId}/reports/portfolio`),
};