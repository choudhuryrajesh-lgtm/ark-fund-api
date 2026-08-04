export default function SearchBox({ value, onChange, placeholder, count, total }) {
  return (
    <div className="search-row">
      <div className="search-box">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          type="search"
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
        {value && (
          <button type="button" className="search-clear" onClick={() => onChange("")} aria-label="Clear search">
            ×
          </button>
        )}
      </div>
      {value && (
        <span className="search-count">
          {count} of {total}
        </span>
      )}
    </div>
  );
}