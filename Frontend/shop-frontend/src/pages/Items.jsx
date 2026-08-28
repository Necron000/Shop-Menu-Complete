import { useEffect, useState } from "react";
import { api } from "../api";
import { useAuth } from "../AuthContext";
import { useCart } from "../CartContext";
import { Link } from "react-router-dom";

export default function Items() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const { isLoggedIn } = useAuth();
  const { addItem, quantityOf } = useCart();

  async function loadItems() {
    try {
      setItems(await api.getItems());
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadItems();
  }, []);

  if (loading) return <p>Loading items…</p>;

  return (
    <div>
      <h2>Shop</h2>
      {error && <p className="error">{error}</p>}
      {items.length === 0 && <p>No items available.</p>}

      <div className="grid">
        {items.map((item) => (
          <ItemCard
            key={item.id}
            item={item}
            canBuy={isLoggedIn}
            inCart={quantityOf(item.id)}
            onAdd={addItem}
          />
        ))}
      </div>

      {!isLoggedIn && (
        <p className="hint">
          <Link to="/login">Log in</Link> to buy items.
        </p>
      )}
    </div>
  );
}

function ItemCard({ item, canBuy, inCart, onAdd }) {
  const [quantity, setQuantity] = useState(1);

  const outOfStock = item.stock === 0;

  return (
    <div className="card">
      <h3>{item.name}</h3>
      <p className="desc">{item.description}</p>
      <p className="price">
        {item.price} {item.currency}
      </p>
      <p className={outOfStock ? "error" : "stock"}>
        {outOfStock ? "Out of stock" : `${item.stock} in stock`}
      </p>

      {canBuy && !outOfStock && (
        <>
          <div className="buy-row">
            <input
              type="number"
              min={1}
              max={item.stock}
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
            />
            <button onClick={() => onAdd(item, quantity)}>Add to cart</button>
          </div>
          {inCart > 0 && <p className="hint">{inCart} in your cart</p>}
        </>
      )}
    </div>
  );
}
