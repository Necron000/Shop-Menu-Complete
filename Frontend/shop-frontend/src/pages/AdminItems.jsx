import { useEffect, useState } from "react";
import { api } from "../api";

const EMPTY = {
  name: "",
  description: "",
  price: "",
  currency: "TRY",
  stock: "",
};

export default function AdminItems() {
  const [form, setForm] = useState(EMPTY);
  const [items, setItems] = useState([]);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  async function loadItems() {
    try {
      setItems(await api.getItems());
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    loadItems();
  }, []);

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setBusy(true);

    try {
      const created = await api.createItem({
        name: form.name,
        description: form.description,
        price: Number(form.price),
        currency: form.currency,
        stock: Number(form.stock),
      });
      setMessage(`Created "${created.name}" (id ${created.id})`);
      setForm(EMPTY);
      await loadItems();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h2>Admin — create item</h2>

      <form className="card narrow" onSubmit={handleSubmit}>
        <label>
          Name
          <input
            value={form.name}
            onChange={(e) => update("name", e.target.value)}
            required
          />
        </label>
        <label>
          Description
          <textarea
            value={form.description}
            onChange={(e) => update("description", e.target.value)}
            rows={3}
          />
        </label>
        <label>
          Price
          <input
            type="number"
            step="0.01"
            min="0.01"
            value={form.price}
            onChange={(e) => update("price", e.target.value)}
            required
          />
        </label>
        <label>
          Currency
          <input
            value={form.currency}
            onChange={(e) => update("currency", e.target.value.toUpperCase())}
            maxLength={3}
            required
          />
        </label>
        <label>
          Stock
          <input
            type="number"
            min="0"
            value={form.stock}
            onChange={(e) => update("stock", e.target.value)}
            required
          />
        </label>

        {error && <p className="error">{error}</p>}
        {message && <p className="success">{message}</p>}

        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create item"}
        </button>
      </form>

      <h3>Existing items</h3>
      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Price</th>
            <th>Stock</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>{item.name}</td>
              <td>
                {item.price} {item.currency}
              </td>
              <td>{item.stock}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}