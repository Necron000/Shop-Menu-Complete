import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";

const FINAL_STATUSES = ["PAID", "FAILED", "CANCELLED"];
const POLL_INTERVAL_MS = 2000;
const MAX_ATTEMPTS = 15;

const HEADLINES = {
  PAID: "Payment successful",
  FAILED: "Payment failed",
  CANCELLED: "Payment cancelled",
};

export default function OrderResult() {
  const { id } = useParams();

  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(true);

  useEffect(() => {
    let cancelled = false;
    let timer = null;
    let attempts = 0;

    async function poll() {
      attempts += 1;

      try {
        const fetched = await api.getOrder(id);
        if (cancelled) return;

        setOrder(fetched);

        if (FINAL_STATUSES.includes(fetched.status)) {
          setPending(false);
          return;
        }
      } catch (err) {
        if (cancelled) return;
        setError(err.message);
        setPending(false);
        return;
      }

      if (attempts >= MAX_ATTEMPTS) {
        setPending(false);
        return;
      }

      timer = setTimeout(poll, POLL_INTERVAL_MS);
    }

    poll();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [id]);

  if (error) return <p className="error">{error}</p>;
  if (!order) return <p>Loading order…</p>;

  const settled = FINAL_STATUSES.includes(order.status);

  return (
    <div>
      <h2>{settled ? HEADLINES[order.status] : "Waiting for payment confirmation…"}</h2>

      <div className="card">
        <p>Order #{order.id}</p>
        <ul className="line-list">
          {order.items?.map((line) => (
            <li key={line.itemId}>
              {line.quantity} × {line.itemName} — {line.lineTotal} {order.currency}
            </li>
          ))}
        </ul>
        <p className="price">
          {order.amount} {order.currency}
        </p>
        <p>
          <span className={`badge ${order.status?.toLowerCase()}`}>{order.status}</span>
        </p>
        {order.errorMessage && <p className="error">{order.errorMessage}</p>}
        {pending && <p className="hint">Checking again in a moment…</p>}
        {!settled && !pending && (
          <p className="hint">
            Still not confirmed. Check <Link to="/orders">your orders</Link> later.
          </p>
        )}
      </div>

      <p className="hint">
        <Link to="/orders">My orders</Link> · <Link to="/">Back to shop</Link>
      </p>
    </div>
  );
}
