import { useEffect, useState } from "react";
import { api } from "../api";
import { formatMoney } from "../format";

export default function ReportsPanel({ clientId }) {
  const [view, setView] = useState("portfolio");
  const [funds, setFunds] = useState([]);
  const [investors, setInvestors] = useState([]);
  const [selectedFundId, setSelectedFundId] = useState("");
  const [selectedInvestorId, setSelectedInvestorId] = useState("");
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.listFunds(clientId).then((p) => setFunds(p.content)).catch(() => {});
    api.listInvestors(clientId).then((p) => setInvestors(p.content)).catch(() => {});
    setSelectedFundId("");
    setSelectedInvestorId("");
    setReport(null);
  }, [clientId]);

  useEffect(() => {
    if (view === "portfolio") loadPortfolio();
    else setReport(null);
  }, [clientId, view]);

  async function loadPortfolio() {
    setLoading(true);
    setError(null);
    try {
      setReport(await api.portfolioReport(clientId));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadFundReport(fundId) {
    setSelectedFundId(fundId);
    if (!fundId) return setReport(null);
    setLoading(true);
    setError(null);
    try {
      setReport(await api.fundReport(clientId, fundId));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadInvestorReport(investorId) {
    setSelectedInvestorId(investorId);
    if (!investorId) return setReport(null);
    setLoading(true);
    setError(null);
    try {
      setReport(await api.investorReport(clientId, investorId));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="report-tabs">
        <button className={view === "portfolio" ? "active" : ""} onClick={() => setView("portfolio")}>
          Portfolio
        </button>
        <button className={view === "fund" ? "active" : ""} onClick={() => setView("fund")}>
          By fund
        </button>
        <button className={view === "investor" ? "active" : ""} onClick={() => setView("investor")}>
          By investor
        </button>
      </div>

      {view === "fund" && (
        <select value={selectedFundId} onChange={(e) => loadFundReport(e.target.value)}>
          <option value="">Select a fund…</option>
          {funds.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
      )}
      {view === "investor" && (
        <select value={selectedInvestorId} onChange={(e) => loadInvestorReport(e.target.value)}>
          <option value="">Select an investor…</option>
          {investors.map((i) => (
            <option key={i.id} value={i.id}>
              {i.name}
            </option>
          ))}
        </select>
      )}

      {error && <p className="error">{error}</p>}
      {loading && <p>Loading…</p>}

      {!loading && report && (
        <div className="report">
          <div className="totals-grid">
            <div>
              <span className="label">As of</span>
              <span>{report.asOfDate}</span>
            </div>
            <div>
              <span className="label">Credits</span>
              <span className="num">{formatMoney(report.totals.totalCredits)}</span>
            </div>
            <div>
              <span className="label">Debits</span>
              <span className="num">{formatMoney(report.totals.totalDebits)}</span>
            </div>
            <div>
              <span className="label">Net</span>
              <span className="num strong">{formatMoney(report.totals.netBalance)}</span>
            </div>
          </div>

          <h4>By type</h4>
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(report.totals.byType).map(([type, amount]) => (
                <tr key={type}>
                  <td>{type}</td>
                  <td className="num">{formatMoney(amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {report.investorPositions && (
            <>
              <h4>Investor positions</h4>
              <table>
                <thead>
                  <tr>
                    <th>Investor</th>
                    <th>Net position</th>
                  </tr>
                </thead>
                <tbody>
                  {report.investorPositions.map((p) => (
                    <tr key={p.investorId}>
                      <td>{p.investorName}</td>
                      <td className="num">{formatMoney(p.totals.netBalance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {report.fundPositions && (
            <>
              <h4>Fund positions</h4>
              <table>
                <thead>
                  <tr>
                    <th>Fund</th>
                    <th>Net position</th>
                  </tr>
                </thead>
                <tbody>
                  {report.fundPositions.map((p) => (
                    <tr key={p.fundId}>
                      <td>{p.fundName}</td>
                      <td className="num">{formatMoney(p.totals.netBalance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {report.funds && (
            <>
              <h4>Funds</h4>
              <table>
                <thead>
                  <tr>
                    <th>Fund</th>
                    <th>Investors</th>
                    <th>Net balance</th>
                  </tr>
                </thead>
                <tbody>
                  {report.funds.map((f) => (
                    <tr key={f.fundId}>
                      <td>{f.fundName}</td>
                      <td>{f.investorCount}</td>
                      <td className="num">{formatMoney(f.netBalance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </div>
      )}
    </div>
  );
}