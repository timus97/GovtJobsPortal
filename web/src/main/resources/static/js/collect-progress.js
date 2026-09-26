(() => {
  const card = document.querySelector("[data-progress-url]");
  if (!card) return;
  const url = card.getAttribute("data-progress-url");
  const pre = card.querySelector("pre");
  const poll = () => {
    fetch(url, { credentials: "same-origin" })
      .then((r) => (r.ok ? r.json() : null))
      .then((body) => {
        if (body && pre) pre.textContent = JSON.stringify(body, null, 2);
      })
      .catch(() => {});
  };
  poll();
  window.setInterval(poll, 4000);
})();
