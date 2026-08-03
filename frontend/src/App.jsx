import { useEffect, useState } from "react";
import { api } from "./api";
import ClientPicker from "./components/ClientPicker";
import FundsPanel from "./components/FundsPanel";
import InvestorsPanel from "./components/InvestorsPanel";
import TransactionsPanel from "./components/TransactionsPanel";
import ReportsPanel from "./components/ReportsPanel";

const TABS = [
  { key: "funds", label: "Funds", icon: <path d="M3 3v18h18M7 15l4-6 3 3 5-8" /> },
  {
    key: "investors",
    label: "Investors",
    icon: <path d="M17 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" />,
  },
  {
    key: "transactions",
    label: "Transactions",
    icon: <path d="M17 3 21 7l-4 4M3 17l4 4 4-4M21 7H7a4 4 0 0 0-4 4M3 17h14a4 4 0 0 0 4-4" />,
  },
  { key: "reports", label: "Reports", icon: <path d="M4 20V10M12 20V4M20 20v-7" /> },
];

function TabIcon({ children }) {
  return (
    <svg className="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}

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
      <header className="app-header">
        <div className="wordmark">
          <span className="mark">
            <em>Ark</em> Fund
          </span>
          <span className="kicker">demo ui</span>
        </div>
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
                <TabIcon>{t.icon}</TabIcon>
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