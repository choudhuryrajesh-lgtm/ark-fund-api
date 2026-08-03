import { useEffect, useState } from "react";
import { api } from "../api";

export default function InvestorsPanel({ clientId }) {
  const [investors, setInvestors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ name: "", email: "" });

  async function load() {
    setLoading(true);
    try {
      const page = await api.listInvestors(clientId);
      setInvestors(page.content);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [clientId]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.createInvestor(clientId, form);
      setForm({ name: "", email: "" });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="panel">
      <form onSubmit={handleSubmit} className="inline-form wrap">
        <label className="field">
          Investor name
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        </label>
        <label className="field">
          Email
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? "Adding…" : "Add investor"}
        </button>
      </form>
      {error && <p className="error">{error}</p>}

      {loading ? (
        <p>Loading…</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
            </tr>
          </thead>
          <tbody>
            {investors.map((i) => (
              <tr key={i.id}>
                <td>{i.name}</td>
                <td>{i.email}</td>
              </tr>
            ))}
            {investors.length === 0 && (
              <tr>
                <td colSpan={2} className="empty">
                  No investors yet
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}