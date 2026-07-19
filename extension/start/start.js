"use strict";

function updateClock() {
  const now = new Date();
  document.getElementById("time").textContent = new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit"
  }).format(now);
  document.getElementById("date").textContent = new Intl.DateTimeFormat(undefined, {
    weekday: "long",
    month: "short",
    day: "numeric"
  }).format(now);
}

updateClock();
window.setInterval(updateClock, 30_000);

