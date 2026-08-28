import { useEffect, useRef, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../AuthContext";
import { useCart } from "../CartContext";

const EMPTY_FORM = {
  name: "",
  surname: "",
  phone: "",
  identityNumber: "",
  address: "",
  city: "",
  country: "Turkey",
  zipCode: "",
};

const DETAILS_KEY = "checkoutDetails";

function readSavedDetails() {
  try {
    return { ...EMPTY_FORM, ...JSON.parse(localStorage.getItem(DETAILS_KEY)) };
  } catch {
    return EMPTY_FORM;
  }
}

// Scripts set via innerHTML are inert, so each one is recreated to actually run.
function injectCheckoutForm(container, html) {
  container.innerHTML = html;

  container.querySelectorAll("script").forEach((original) => {
    const script = document.createElement("script");
    for (const { name, value } of original.attributes) {
      script.setAttribute(name, value);
    }
    script.text = original.text;
    original.replaceWith(script);
  });
}

export default function Checkout() {
  const { auth } = useAuth();
  const { lines, total, currency, clear } = useCart();

  const [form, setForm] = useState(readSavedDetails);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formHtml, setFormHtml] = useState(null);

  const formContainer = useRef(null);

  useEffect(() => {
    if (formHtml && formContainer.current) {
      injectCheckoutForm(formContainer.current, formHtml);
    }
  }, [formHtml]);

  function update(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await api.initializeCheckout({
        items: lines.map((line) => ({ itemId: line.itemId, quantity: line.quantity })),
        email: auth.email,
        ...form,
      });

      if (!response.checkoutFormContent) {
        setError(response.errorMessage || "Iyzico did not return a payment form.");
        return;
      }

      localStorage.setItem(DETAILS_KEY, JSON.stringify(form));

      clear();
      setFormHtml(response.checkoutFormContent);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (formHtml) {
    return (
      <div>
        <h2>Payment</h2>
        <p className="hint">Complete the payment in the form below.</p>
        <div ref={formContainer} />
        <div id="iyzipay-checkout-form" className="responsive" />
      </div>
    );
  }

  if (lines.length === 0) return <Navigate to="/cart" replace />;

  return (
    <div>
      <h2>Checkout</h2>

      <div className="card summary">
        <h3>Order summary</h3>
        <ul>
          {lines.map((line) => (
            <li key={line.itemId}>
              {line.quantity} × {line.name} — {(line.price * line.quantity).toFixed(2)}{" "}
              {line.currency}
            </li>
          ))}
        </ul>
        <p className="price">
          Total: {total.toFixed(2)} {currency}
        </p>
        <p className="hint">
          <Link to="/cart">Edit cart</Link>
        </p>
      </div>

      {error && <p className="error">{error}</p>}

      <form className="card" onSubmit={handleSubmit}>
        <label>
          Name
          <input value={form.name} onChange={update("name")} required maxLength={100} />
        </label>
        <label>
          Surname
          <input value={form.surname} onChange={update("surname")} required maxLength={100} />
        </label>
        <label>
          Phone
          <input value={form.phone} onChange={update("phone")} required maxLength={30} />
        </label>
        <label>
          Identity number
          <input
            value={form.identityNumber}
            onChange={update("identityNumber")}
            required
            pattern="\d{11}"
            title="11 digits"
          />
        </label>
        <label>
          Address
          <textarea value={form.address} onChange={update("address")} required rows={2} />
        </label>
        <label>
          City
          <input value={form.city} onChange={update("city")} required maxLength={100} />
        </label>
        <label>
          Country
          <input value={form.country} onChange={update("country")} required maxLength={100} />
        </label>
        <label>
          Zip code
          <input value={form.zipCode} onChange={update("zipCode")} required maxLength={20} />
        </label>

        <button type="submit" disabled={submitting}>
          {submitting ? "Preparing payment…" : "Continue to payment"}
        </button>
      </form>
    </div>
  );
}
