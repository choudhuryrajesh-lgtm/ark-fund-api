// Shared credit/debit badge — used in the transactions table and could be
// reused anywhere else a transaction type needs to read at a glance.
export default function TypePill({ type, direction }) {
  const isCredit = direction === "CREDIT";
  return <span className={`pill ${isCredit ? "credit" : "debit"}`}>{type}</span>;
}