import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { formatMoney } from "../format";
import DeleteButton from "./DeleteButton";
import SearchBox from "./SearchBox";
import TypePill from "./TypePill";

const EMPTY_FORM = { fundId: "", investorId: "", type: "", amount: "", transactionDate: "", notes: "" };
const EMPTY_FILTERS = { fundId: "", investorId: "" };

export default function TransactionsPanel({ clientId }) {
  const [transactions, setTransactions] = useState([]);
  const [funds, setFunds] = useState([]);
  const [investors, setInvestors] = useState([]);
  const [types, setTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [query, setQuery] = useState("");

  // Reference data (funds, investors, types) changes far less often than the
  // ledger, so it loads once per client rather than on every filter change.
  async function loadReferenceData() {
    const [fundsPage, investorsPage, typeList] = await Promise.all([
      api.listFunds(clientId),
      api.listInvestors(clientId),
      api.listTransactionTypes(),
    ]);
    setFunds(fundsPage.content);
    setInvestors(investorsPage.content);
    setTypes(typeList);
  }

  async function loadTransactions(active = filters) {
    const txPage = await api.listTransactions(clientId, active);
    setTransactions(txPage.content);
  }

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFilters(EMPTY_FILTERS);
    setQuery("");
    (async () => {
      try {
        await Promise.all([loadReferenceData(), loadTransactions(EMPTY_FILTERS)]);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [clientId]);

  // Fund/investor narrowing goes back to the API rather than filtering the
  // page already in hand — the endpoint takes both as query params, and the
  // browser only ever holds one page of the ledger.
  async function applyFilter(next) {
    setFilters(next);
    setError(null);
    try {
      await loadTransactions(next);
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDelete(tx) {
    setError(null);
    try {
      await api.deleteTransaction(clientId, tx.id);
      await loadTransactions();
    } catch (err) {
      setError(err.message);
    }
  }

  // Free-text search stays client-side over the returned page: there is no
  // server-side text search on this endpoint, and inventing one in the UI
  // would quietly only ever search the page you happen to be looking at.
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return transactions;
    return transactions.filter((t) =>
      [t.fundName, t.investorName, t.type, t.notes, t.transactionDate, String(t.amount)]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q))
    );
  }, [transactions, query]);

  const filtered = filters.fundId || filters.investorId;

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.createTransaction(clientId, { ...form, amount: Number(form.amount) });
      setForm(EMPTY_FORM);
      await loadTransactions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p>Loading…</p>;

  const noPartiesYet = funds.length === 0 || investors.length === 0;

  return (
    <div className="panel">
      {noPartiesYet && (
        <p className="hint">Add at least one fund and one investor before recording a transaction.</p>
      )}

      <form onSubmit={handleSubmit} className="inline-form wrap">
        <label className="field">
          Fund
          <select
            value={form.fundId}
            onChange={(e) => setForm({ ...form, fundId: e.target.value })}
            required
          >
            <option value="">Select…</option>
            {funds.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Investor
          <select
            value={form.investorId}
            onChange={(e) => setForm({ ...form, investorId: e.target.value })}
            required
          >
            <option value="">Select…</option>
            {investors.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Type
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} required>
            <option value="">Select…</option>
            {types.map((t) => (
              <option key={t.code} value={t.code}>
                {t.code} ({t.direction})
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Amount
          <input
            type="number"
            step="0.01"
            min="0.01"
            value={form.amount}
            onChange={(e) => setForm({ ...form, amount: e.target.value })}
            required
          />
        </label>
        <label className="field">
          Date
          <input
            type="date"
            value={form.transactionDate}
            onChange={(e) => setForm({ ...form, transactionDate: e.target.value })}
            required
          />
        </label>
        <label className="field">
          Notes
          <input
            value={form.notes}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
          />
        </label>
        <button type="submit" disabled={submitting || noPartiesYet}>
          {submitting ? "Recording…" : "Record transaction"}
        </button>
      </form>
      {error && <p className="error">{error}</p>}

      <div className="filter-bar">
        <label className="field">
          Filter by fund
          <select
            value={filters.fundId}
            onChange={(e) => applyFilter({ ...filters, fundId: e.target.value })}
          >
            <option value="">All funds</option>
            {funds.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Filter by investor
          <select
            value={filters.investorId}
            onChange={(e) => applyFilter({ ...filters, investorId: e.target.value })}
          >
            <option value="">All investors</option>
            {investors.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name}
              </option>
            ))}
          </select>
        </label>
        {filtered && (
          <button type="button" className="link-button" onClick={() => applyFilter(EMPTY_FILTERS)}>
            Clear filters
          </button>
        )}
      </div>

      <SearchBox
        value={query}
        onChange={setQuery}
        placeholder="Search these transactions by fund, investor, type, amount or notes…"
        count={visible.length}
        total={transactions.length}
      />

      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Fund</th>
            <th>Investor</th>
            <th>Type</th>
            <th>Amount</th>
            <th>Notes</th>
            <th className="actions-col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((t) => (
            <tr key={t.id}>
              <td>{t.transactionDate}</td>
              <td>{t.fundName}</td>
              <td>{t.investorName}</td>
              <td>
                <TypePill type={t.type} direction={t.direction} />
              </td>
              <td className="num">{formatMoney(t.amount)}</td>
              <td>{t.notes ?? "—"}</td>
              <td className="actions-col">
                <DeleteButton onConfirm={() => handleDelete(t)} />
              </td>
            </tr>
          ))}
          {visible.length === 0 && (
            <tr>
              <td colSpan={7} className="empty">
                {transactions.length === 0
                  ? filtered
                    ? "No transactions match those filters"
                    : "No transactions yet"
                  : `No transactions matching "${query}"`}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}