import { useEffect, useState } from "react";
import { api } from "./api";
import ClientPicker from "./components/ClientPicker";
import FundsPanel from "./components/FundsPanel";
import InvestorsPanel from "./components/InvestorsPanel";
import TransactionsPanel from "./components/TransactionsPanel";
import ReportsPanel from "./components/ReportsPanel";

const TABS = [
  { key: "funds", label: "Funds" },
  { key: "investors", label: "Investors" },
  { key: "transactions", label: "Transactions" },
  { key: "reports", label: "Reports" },
];

export default function App() {
  const [clients, setClients] = useState([]);
  const [selectedClientId, setSelectedClientId] = useState(null);
  const [activeTab, setActiveTab] = useState("funds");
  const [error, setError] = useState(null);

  async function loadClients() {
    try {
      const page = await api.listClients();
      setClients(page.content);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    loadClients();
  }, []);

  async function handleCreateClient(body) {
    const created = await api.createClient(body);
    await loadClients();
    setSelectedClientId(created.id);
  }

  return (
    <div className="app">
      <header>
        <h1>Ark Fund API — Demo UI</h1>
        <p className="subtitle">
          A minimal showcase talking to the real API. Not part of the graded submission — see{" "}
          <code>frontend/README.md</code>.
        </p>
      </header>

      {error && <p className="error">{error}</p>}

      <ClientPicker
        clients={clients}
        selectedClientId={selectedClientId}
        onSelect={setSelectedClientId}
        onCreated={handleCreateClient}
      />

      {selectedClientId && (
        <>
          <nav className="tabs">
            {TABS.map((t) => (
              <button
                key={t.key}
                className={activeTab === t.key ? "active" : ""}
                onClick={() => setActiveTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </nav>

          {activeTab === "funds" && <FundsPanel clientId={selectedClientId} />}
          {activeTab === "investors" && <InvestorsPanel clientId={selectedClientId} />}
          {activeTab === "transactions" && <TransactionsPanel clientId={selectedClientId} />}
          {activeTab === "reports" && <ReportsPanel clientId={selectedClientId} />}
        </>
      )}
    </div>
  );
}