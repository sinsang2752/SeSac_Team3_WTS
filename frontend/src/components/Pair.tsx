import type { ReactNode } from 'react'

interface Props {
  label: ReactNode
  value: ReactNode
  /** 묶음에서 가장 중요한 값(주문가능금액·매도가능수량)에만 쓴다. */
  emphasis?: boolean
  /** 값에 얹을 등락색 등의 추가 클래스. */
  valueClassName?: string
}

/** 이름과 값 한 쌍. 자산 상세·주문 요약·확인 모달이 공유한다. */
export function Pair({ label, value, emphasis, valueClassName }: Props) {
  return (
    <div className={`pair${emphasis ? ' pair--emphasis' : ''}`}>
      <span>{label}</span>
      <strong className={`pair__value${valueClassName ? ` ${valueClassName}` : ''}`}>
        {value}
      </strong>
    </div>
  )
}
