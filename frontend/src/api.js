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

const del = (path) => request(path, { method: "DELETE" });

export const api = {
  listClients: () => request("/clients?size=100"),
  createClient: (body) => request("/clients", { method: "POST", body: JSON.stringify(body) }),
  deleteClient: (clientId) => del(`/clients/${clientId}`),

  listFunds: (clientId) => request(`/clients/${clientId}/funds?size=100`),
  createFund: (clientId, body) =>
    request(`/clients/${clientId}/funds`, { method: "POST", body: JSON.stringify(body) }),
  deleteFund: (clientId, fundId) => del(`/clients/${clientId}/funds/${fundId}`),

  listInvestors: (clientId) => request(`/clients/${clientId}/investors?size=100`),
  createInvestor: (clientId, body) =>
    request(`/clients/${clientId}/investors`, { method: "POST", body: JSON.stringify(body) }),
  deleteInvestor: (clientId, investorId) => del(`/clients/${clientId}/investors/${investorId}`),

  // fundId / investorId are filtered server-side — the API supports both as
  // query params, so narrowing by party doesn't mean pulling the whole ledger
  // down and filtering it in the browser.
  listTransactions: (clientId, { fundId, investorId } = {}) => {
    const params = new URLSearchParams({ size: "50", sort: "transactionDate,desc" });
    if (fundId) params.set("fundId", fundId);
    if (investorId) params.set("investorId", investorId);
    return request(`/clients/${clientId}/transactions?${params}`);
  },
  createTransaction: (clientId, body) =>
    request(`/clients/${clientId}/transactions`, { method: "POST", body: JSON.stringify(body) }),
  deleteTransaction: (clientId, transactionId) =>
    del(`/clients/${clientId}/transactions/${transactionId}`),

  listTransactionTypes: () => request("/transaction-types"),

  fundReport: (clientId, fundId) => request(`/clients/${clientId}/reports/funds/${fundId}`),
  investorReport: (clientId, investorId) =>
    request(`/clients/${clientId}/reports/investors/${investorId}`),
  portfolioReport: (clientId) => request(`/clients/${clientId}/reports/portfolio`),
};