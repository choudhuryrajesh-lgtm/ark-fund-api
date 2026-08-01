import { useState } from "react";

export default function ClientPicker({ clients, selectedClientId, onSelect, onCreated }) {
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onCreated({ name, email });
      setName("");
      setEmail("");
      setShowForm(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2>Client</h2>
        <button className="link-button" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Cancel" : "+ New client"}
        </button>
      </div>

      {!showForm && (
        <select value={selectedClientId ?? ""} onChange={(e) => onSelect(e.target.value || null)}>
          <option value="">Select a client…</option>
          {clients.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <input
            placeholder="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <button type="submit" disabled={submitting}>
            {submitting ? "Creating…" : "Create"}
          </button>
        </form>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  );
}