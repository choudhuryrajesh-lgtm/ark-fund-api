import { useEffect, useState } from "react";
import { api } from "../api";
import { formatMoney } from "../format";
import TypePill from "./TypePill";

const EMPTY_FORM = { fundId: "", investorId: "", type: "", amount: "", transactionDate: "", notes: "" };

export default function TransactionsPanel({ clientId }) {
  const [transactions, setTransactions] = useState([]);
  const [funds, setFunds] = useState([]);
  const [investors, setInvestors] = useState([]);
  const [types, setTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  async function loadAll() {
    setLoading(true);
    try {
      const [txPage, fundsPage, investorsPage, typeList] = await Promise.all([
        api.listTransactions(clientId),
        api.listFunds(clientId),
        api.listInvestors(clientId),
        api.listTransactionTypes(),
      ]);
      setTransactions(txPage.content);
      setFunds(fundsPage.content);
      setInvestors(investorsPage.content);
      setTypes(typeList);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, [clientId]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.createTransaction(clientId, { ...form, amount: Number(form.amount) });
      setForm(EMPTY_FORM);
      await loadAll();
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

      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Fund</th>
            <th>Investor</th>
            <th>Type</th>
            <th>Amount</th>
            <th>Notes</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => (
            <tr key={t.id}>
              <td>{t.transactionDate}</td>
              <td>{t.fundName}</td>
              <td>{t.investorName}</td>
              <td>
                <TypePill type={t.type} direction={t.direction} />
              </td>
              <td className="num">{formatMoney(t.amount)}</td>
              <td>{t.notes ?? "—"}</td>
            </tr>
          ))}
          {transactions.length === 0 && (
            <tr>
              <td colSpan={6} className="empty">
                No transactions yet
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}