let observer=null;
export function clearInsightLayout(){observer?.disconnect();observer=null}
export function layoutInsightCards(root){
  clearInsightLayout();
  const grid=root.querySelector('.compact-insights');
  if(!grid)return;
  const measure=()=>{
    for(const card of grid.children)card.style.gridRowEnd='span '+Math.ceil(card.getBoundingClientRect().height+12);
  };
  measure();
  let width=grid.getBoundingClientRect().width;
  observer=new ResizeObserver(entries=>{
    const next=entries[0].contentRect.width;
    if(Math.abs(next-width)>.5){width=next;measure()}
  });
  observer.observe(grid);
}
