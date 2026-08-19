export default function OpsRequiresApi() {
  return (
    <div className="section">
      <div className="container">
        <div className="panel ops-card ops-login-card">
          <p className="eyebrow">Operators</p>
          <h1>Ops requires the API host</h1>
          <p className="lead">
            This is a static snapshot (for example GitHub Pages). Operator sign-in and the collect
            dashboard run on the always-on Express API.
          </p>
          <p className="muted" style={{ marginBottom: 0 }}>
            Open the Render (or local <code>npm run server</code>) host and go to <code>/ops</code>.
            The public job list still works here.
          </p>
        </div>
      </div>
    </div>
  )
}
