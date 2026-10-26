export default function Field(p: {
  id: string; label: string; value: string; onChange(v: string): void; error?: string; type?: string; autoComplete?: string
}) {
  return (
    <div className="field">
      <label htmlFor={p.id}>{p.label}</label>
      <input id={p.id} type={p.type ?? 'text'} value={p.value} autoComplete={p.autoComplete}
        aria-invalid={!!p.error} aria-describedby={p.error ? `${p.id}-err` : undefined}
        onChange={(e) => p.onChange(e.target.value)} />
      {p.error && <span className="err" id={`${p.id}-err`}>{p.error}</span>}
    </div>
  )
}
