import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../CartContext";

export default function Cart() {
  const { lines, setQuantity, removeItem, clear, total, currency } = useCart();
  const navigate = useNavigate();

  if (lines.length === 0) {
    return (
      <div>
        <h2>Your cart</h2>
        <p>Your cart is empty.</p>
        <p className="hint">
          <Link to="/">Back to shop</Link>
        </p>
      </div>
    );
  }

  return (
    <div>
      <h2>Your cart</h2>

      <table className="table">
        <thead>
          <tr>
            <th>Item</th>
            <th>Price</th>
            <th>Qty</th>
            <th>Line total</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.itemId}>
              <td>{line.name}</td>
              <td>
                {line.price} {line.currency}
              </td>
              <td>
                <input
                  className="qty"
                  type="number"
                  min={1}
                  max={line.stock}
                  value={line.quantity}
                  onChange={(e) => setQuantity(line.itemId, Number(e.target.value))}
                />
              </td>
              <td>
                {(line.price * line.quantity).toFixed(2)} {line.currency}
              </td>
              <td>
                <button className="link" onClick={() => removeItem(line.itemId)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="cart-footer">
        <p className="price">
          Total: {total.toFixed(2)} {currency}
        </p>
        <div className="cart-actions">
          <button className="secondary" onClick={clear}>
            Clear cart
          </button>
          <button onClick={() => navigate("/checkout")}>Checkout</button>
        </div>
      </div>
    </div>
  );
}
