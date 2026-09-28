import { useToastStore } from '../../../stores/toastStore'
import { useToggleWatchlist, useWatchlist } from '../useWatchlist'

interface Props {
  symbol: string
  /** 알림 문구에 쓴다. 없으면 종목코드로 대신한다. */
  name?: string
}

/** 관심종목 담기/빼기 토글. (CLAUDE.md §28, ui-requirements §5.2) */
export function WatchStar({ symbol, name }: Props) {
  const { data: watchlist } = useWatchlist()
  const toggle = useToggleWatchlist()
  const pushToast = useToastStore((state) => state.push)

  const watched = (watchlist ?? []).some((item) => item.symbol === symbol)
  const label = name ?? symbol

  return (
    <button
      type="button"
      className={`star${watched ? ' star--on' : ''}`}
      aria-label={watched ? `${label} 관심종목에서 빼기` : `${label} 관심종목에 담기`}
      aria-pressed={watched}
      disabled={toggle.isPending}
      onClick={(event) => {
        // 목록 항목 안에 있으므로 종목 선택까지 일어나지 않게 막는다.
        event.stopPropagation()
        toggle.mutate(
          { symbol, watched },
          {
            onSuccess: () =>
              pushToast(
                watched ? `${label} 관심종목에서 뺐어요.` : `${label} 관심종목에 담았어요.`,
              ),
            onError: () => pushToast('관심종목을 바꾸지 못했습니다.', 'error'),
          },
        )
      }}
    >
      {watched ? '★' : '☆'}
    </button>
  )
}
