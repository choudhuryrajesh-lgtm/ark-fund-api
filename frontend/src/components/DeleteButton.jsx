import { useEffect, useState } from "react";

/**
 * Two-step delete: the first click arms it, the second confirms. Deleting a
 * fund or an investor is not reversible from this UI, so a single stray click
 * shouldn't do it — and an inline confirm beats window.confirm, which blocks
 * the page and can't show what is about to be removed.
 *
 * The armed state disarms itself after a few seconds so a button left armed
 * and forgotten doesn't turn the next click into a delete.
 */
export default function DeleteButton({ onConfirm, label = "Delete", busyLabel = "Deleting…" }) {
  const [armed, setArmed] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!armed) return undefined;
    const timer = setTimeout(() => setArmed(false), 4000);
    return () => clearTimeout(timer);
  }, [armed]);

  async function handleClick() {
    if (!armed) {
      setArmed(true);
      return;
    }
    setBusy(true);
    try {
      await onConfirm();
    } finally {
      // The row usually unmounts on success; guard for the failure path,
      // where it stays on screen and must not be stuck on "Deleting…".
      setBusy(false);
      setArmed(false);
    }
  }

  return (
    <button
      type="button"
      className={`delete-button${armed ? " armed" : ""}`}
      onClick={handleClick}
      disabled={busy}
    >
      {busy ? busyLabel : armed ? "Confirm?" : label}
    </button>
  );
}