import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import DeleteButton from "./DeleteButton";
import SearchBox from "./SearchBox";

export default function InvestorsPanel({ clientId }) {
  const [investors, setInvestors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [query, setQuery] = useState("");
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

  async function handleDelete(investor) {
    setError(null);
    try {
      await api.deleteInvestor(clientId, investor.id);
      await load();
    } catch (err) {
      // 409 when the investor has transactions — surface the API's reason.
      setError(err.message);
    }
  }

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return investors;
    return investors.filter((i) =>
      [i.name, i.email].filter(Boolean).some((v) => v.toLowerCase().includes(q))
    );
  }, [investors, query]);

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
        <>
          <SearchBox
            value={query}
            onChange={setQuery}
            placeholder="Search investors by name or email…"
            count={visible.length}
            total={investors.length}
          />
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((i) => (
                <tr key={i.id}>
                  <td>{i.name}</td>
                  <td>{i.email}</td>
                  <td className="actions-col">
                    <DeleteButton onConfirm={() => handleDelete(i)} />
                  </td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={3} className="empty">
                    {investors.length === 0 ? "No investors yet" : `No investors matching "${query}"`}
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