(() => {
  const el = document.getElementById("mock-timer");
  const form = document.getElementById("mock-form");
  if (!el || !form) return;
  const minutes = Number(el.getAttribute("data-minutes") || "20");
  let left = Math.max(1, Math.round(minutes * 60));
  const tick = () => {
    const m = String(Math.floor(left / 60)).padStart(2, "0");
    const s = String(left % 60).padStart(2, "0");
    el.textContent = m + ":" + s;
    if (left <= 0) {
      form.submit();
      return;
    }
    left -= 1;
    window.setTimeout(tick, 1000);
  };
  tick();
})();
