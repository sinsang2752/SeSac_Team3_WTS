import type { OrderStatus } from '../../../lib/types'
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE } from '../orderStatus'

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge${ORDER_STATUS_TONE[status]}`}>{ORDER_STATUS_LABEL[status]}</span>
}
