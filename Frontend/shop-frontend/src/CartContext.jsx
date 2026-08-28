import { createContext, useContext, useEffect, useState } from "react";

const CartContext = createContext(null);

const STORAGE_KEY = "cart";

// Price snapshots are display-only; the backend re-prices everything at checkout.
function readStoredCart() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(stored) ? stored : [];
  } catch {
    return [];
  }
}

export function CartProvider({ children }) {
  const [lines, setLines] = useState(readStoredCart);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
  }, [lines]);

  function addItem(item, quantity = 1) {
    setLines((prev) => {
      const existing = prev.find((line) => line.itemId === item.id);

      if (existing) {
        return prev.map((line) =>
          line.itemId === item.id
            ? { ...line, quantity: clamp(line.quantity + quantity, item.stock) }
            : line,
        );
      }

      return [
        ...prev,
        {
          itemId: item.id,
          name: item.name,
          price: item.price,
          currency: item.currency,
          stock: item.stock,
          quantity: clamp(quantity, item.stock),
        },
      ];
    });
  }

  function setQuantity(itemId, quantity) {
    setLines((prev) =>
      prev.map((line) =>
        line.itemId === itemId ? { ...line, quantity: clamp(quantity, line.stock) } : line,
      ),
    );
  }

  function removeItem(itemId) {
    setLines((prev) => prev.filter((line) => line.itemId !== itemId));
  }

  function clear() {
    setLines([]);
  }

  const value = {
    lines,
    addItem,
    setQuantity,
    removeItem,
    clear,
    count: lines.reduce((total, line) => total + line.quantity, 0),
    total: lines.reduce((total, line) => total + line.price * line.quantity, 0),
    currency: lines[0]?.currency ?? null,
    quantityOf: (itemId) => lines.find((line) => line.itemId === itemId)?.quantity ?? 0,
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

function clamp(quantity, stock) {
  const bounded = Math.max(1, Math.floor(quantity) || 1);
  return stock ? Math.min(bounded, stock) : bounded;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error("useCart must be used inside CartProvider");
  return context;
}
