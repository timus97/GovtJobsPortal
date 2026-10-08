(() => {
  const root = document.getElementById("catalog-fetch");
  if (!root) return;
  const url = root.getAttribute("data-status-url");
  let wasRunning = root.getAttribute("data-running") === "true";

  const text = (name, value) => {
    const node = root.querySelector("[data-fetch-" + name + "]");
    if (node) node.textContent = value;
  };

  const paint = (body) => {
    if (!body) return;
    const running = Boolean(body.running);
    text("state", running ? "Running" : "Idle");
    text("progress", (body.completed || 0) + " / " + (body.total || 0));
    text("current", body.currentSource ? body.currentSource : "—");
    if (!running && body.lastSummary) text("summary", body.lastSummary);
    root.querySelectorAll("[data-fetch-actions] button, form button").forEach((button) => {
      button.disabled = running;
    });
    if (wasRunning && !running) {
      window.location.reload();
      return;
    }
    wasRunning = running;
  };

  const poll = () => {
    fetch(url, { credentials: "same-origin" })
      .then((response) => (response.ok ? response.json() : null))
      .then(paint)
      .catch(() => {});
  };
  window.setInterval(poll, 4000);
})();
