import { useEffect, useState } from "react";
import { api } from "../api";

export default function FundsPanel({ clientId }) {
  const [funds, setFunds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ name: "", description: "", inceptionDate: "" });

  async function load() {
    setLoading(true);
    try {
      const page = await api.listFunds(clientId);
      setFunds(page.content);
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
      await api.createFund(clientId, form);
      setForm({ name: "", description: "", inceptionDate: "" });
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
        <input
          placeholder="Fund name"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
        <input
          placeholder="Description (optional)"
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
        <input
          type="date"
          value={form.inceptionDate}
          onChange={(e) => setForm({ ...form, inceptionDate: e.target.value })}
          required
        />
        <button type="submit" disabled={submitting}>
          {submitting ? "Adding…" : "Add fund"}
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
              <th>Description</th>
              <th>Inception</th>
            </tr>
          </thead>
          <tbody>
            {funds.map((f) => (
              <tr key={f.id}>
                <td>{f.name}</td>
                <td>{f.description ?? "—"}</td>
                <td>{f.inceptionDate}</td>
              </tr>
            ))}
            {funds.length === 0 && (
              <tr>
                <td colSpan={3} className="empty">
                  No funds yet
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}