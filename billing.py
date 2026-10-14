"""Compatible recipient relations. Existing customer_id remains the work-site reference."""
def recipient(row):
    return row.get("billing_customer_id") or row.get("customer_id")

def customer_config(data, customers):
    mode=data.get("billing_mode") or "direct"
    target=data.get("default_billing_customer_id") or None
    if mode not in ("direct","via_customer"): raise ValueError("Abrechnung muss Direkt oder Über einen anderen Kunden sein.")
    if mode=="direct":
        if target and target!=data.get("id"): raise ValueError("Direktabrechnung darf keinen anderen Standard-Rechnungsempfänger verwenden.")
        target=None
    else:
        if not target or target==data.get("id") or target not in customers: raise ValueError("Bitte einen anderen vorhandenen Rechnungsempfänger auswählen.")
        visited={data.get("id")};following=target
        while following:
            if following in visited: raise ValueError("Die Standard-Abrechnung würde einen Kreis bilden.")
            visited.add(following)
            row=customers.get(following)
            if row is None: raise ValueError("Standard-Rechnungsempfänger nicht vorhanden.")
            following=row.get("default_billing_customer_id") if row.get("billing_mode")=="via_customer" else None
    return mode,target

def default_recipient(customer_id, order, customers):
    customer=customers.get(customer_id)
    if customer is None: raise ValueError("Der Einsatzkunde existiert nicht.")
    target=customer.get("default_billing_customer_id") if customer.get("billing_mode")=="via_customer" else customer_id
    if order and order.get("billing_customer_id"): target=order["billing_customer_id"]
    if target not in customers: raise ValueError("Der Rechnungsempfänger existiert nicht.")
    return target
