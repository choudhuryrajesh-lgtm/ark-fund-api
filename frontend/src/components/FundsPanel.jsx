import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import DeleteButton from "./DeleteButton";
import SearchBox from "./SearchBox";

export default function FundsPanel({ clientId }) {
  const [funds, setFunds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [query, setQuery] = useState("");
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

  async function handleDelete(fund) {
    setError(null);
    try {
      await api.deleteFund(clientId, fund.id);
      await load();
    } catch (err) {
      // The API refuses to delete a fund that has transactions (409) — show
      // that reason rather than swallowing it, since it's the rule doing its job.
      setError(err.message);
    }
  }

  // Filtered in the browser: the list endpoint has no name-search parameter,
  // and this panel already holds the client's full fund list (size=100).
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return funds;
    return funds.filter((f) =>
      [f.name, f.description].filter(Boolean).some((v) => v.toLowerCase().includes(q))
    );
  }, [funds, query]);

  return (
    <div className="panel">
      <form onSubmit={handleSubmit} className="inline-form wrap">
        <label className="field">
          Fund name
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        </label>
        <label className="field">
          Description
          <input
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </label>
        <label className="field">
          Inception date
          <input
            type="date"
            value={form.inceptionDate}
            onChange={(e) => setForm({ ...form, inceptionDate: e.target.value })}
            required
          />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? "Adding…" : "Add fund"}
        </button>
      </form>
      {error && <p className="error">{error}</p>}

      {loading ? (
        <p>Loading…</p>
      ) : (
        <>
          <SearchBox
            value={query}
            onChange={setQuery}
            placeholder="Search funds by name or description…"
            count={visible.length}
            total={funds.length}
          />
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Description</th>
                <th>Inception</th>
                <th className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((f) => (
                <tr key={f.id}>
                  <td>{f.name}</td>
                  <td>{f.description ?? "—"}</td>
                  <td>{f.inceptionDate}</td>
                  <td className="actions-col">
                    <DeleteButton onConfirm={() => handleDelete(f)} />
                  </td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={4} className="empty">
                    {funds.length === 0 ? "No funds yet" : `No funds matching "${query}"`}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}