import { Modal, Button, Alert } from './ui'

// Diálogo de confirmación reutilizable para acciones sensibles (bajas / borrados).
// `detalle` (opcional) resalta el impacto en un Alert; `tone` controla el color
// del botón de confirmación ('danger' | 'primary').
export default function ConfirmDialog({
  open,
  title = 'Confirmar',
  mensaje,
  detalle,
  detalleTipo = 'error',
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  tone = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}) {
  if (!open) return null
  return (
    <Modal title={title} onClose={loading ? () => {} : onCancel}>
      <div className="space-y-4">
        {mensaje && <p className="text-sm text-ink/80 whitespace-pre-line">{mensaje}</p>}
        {detalle && <Alert type={detalleTipo}>{detalle}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={loading}>{cancelLabel}</Button>
          <Button variant={tone} onClick={onConfirm} disabled={loading}>
            {loading ? 'Procesando…' : confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
