import { Link } from 'react-router'

/**
 * 런스톡 로고.
 *
 * <p>구조와 스타일은 {@code docs/learnstock/reference} 의 원본 시안을 그대로 옮긴 것이다.
 * 심볼은 글자 {@code l} 과 {@code ↗} 의 조합이며, 아이콘이나 SVG로 대체하지 않는다.
 * 크기·모서리·글자 간격·화살표 위치를 임의로 손대면 시안과 달라진다.
 *
 * <p>원본은 {@code <a href="#">}, 여기서는 라우터 {@code <Link>} 다.
 * 프레임워크에 필요한 차이는 그것과 {@code class → className} 뿐이다.
 */
export function LearnstockLogo() {
  return (
    <Link className="logo" to="/wts" aria-label="런스톡 홈">
      <span className="mark">
        l<span>↗</span>
      </span>
      learnstock
      <span className="demo">모의투자</span>
    </Link>
  )
}
