import { useState } from "react";
import DeleteButton from "./DeleteButton";

function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");
}

export default function ClientPicker({ clients, selectedClientId, onSelect, onCreated, onDeleted }) {
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const selected = clients.find((c) => c.id === selectedClientId);

  async function handleDelete() {
    setError(null);
    try {
      await onDeleted(selected);
    } catch (err) {
      setError(err.message);
    }
  }

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
        <p className="eyebrow">Client</p>
        <button className="link-button" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Cancel" : "+ New client"}
        </button>
      </div>

      {!showForm && (
        <div className="client-select-row">
          <div className="client-avatar">{selected ? initials(selected.name) : "—"}</div>
          <select value={selectedClientId ?? ""} onChange={(e) => onSelect(e.target.value || null)}>
            <option value="">Select a client…</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {selected && <DeleteButton onConfirm={handleDelete} label="Delete client" />}
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="inline-form">
          <label className="field">
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="field">
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <button type="submit" disabled={submitting}>
            {submitting ? "Creating…" : "Create"}
          </button>
        </form>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  );
}