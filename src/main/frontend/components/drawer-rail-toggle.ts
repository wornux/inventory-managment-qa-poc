import { LitElement, css, html } from 'lit';
import { customElement, property } from 'lit/decorators.js';

const STORAGE_KEY = 'inventory-drawer-collapsed';
const LAYOUT_CLASS = 'drawer-collapsed';
const WIDTH_EXPANDED_PX = 272;
const WIDTH_COLLAPSED_PX = 72;
const DURATION_MS = 240;

@customElement('drawer-rail-toggle')
export class DrawerRailToggle extends LitElement {
  static styles = css`
    :host {
      display: inline-flex;
      flex-shrink: 0;
    }

    button {
      align-items: center;
      background: transparent;
      border: 0;
      border-radius: var(--vaadin-radius-m, 0.375rem);
      box-sizing: border-box;
      color: var(--vaadin-text-color-secondary, #62676b);
      cursor: pointer;
      display: inline-flex;
      height: 2rem;
      justify-content: center;
      margin: 0;
      min-width: 2rem;
      padding: 0;
      transition: color 150ms ease, background-color 150ms ease;
      width: 2rem;
    }

    button:hover {
      background: var(--vaadin-background-container-strong, rgba(0, 0, 0, 0.06));
      color: var(--vaadin-text-color, #1c1f21);
    }

    button:focus-visible {
      outline: 2px solid var(--aura-accent-color-light, #ff8904);
      outline-offset: 2px;
    }

    .icon {
      display: block;
      flex-shrink: 0;
      height: 18px;
      margin: auto;
      overflow: visible;
      width: 18px;
    }

    .sidebar-line,
    .sidebar-rail,
    .chevron {
      transform-box: view-box;
      transform-origin: center;
      transition: transform 200ms cubic-bezier(0.4, 0, 0.2, 1), opacity 200ms cubic-bezier(0.4, 0, 0.2, 1);
    }

    .icon-open .chevron {
      opacity: 0;
      transform: translateX(2px) scale(0.55);
    }

    button:hover .icon-open .sidebar-line {
      transform: translateX(-2px);
    }

    button:hover .icon-open .sidebar-rail {
      transform: scaleY(0.5);
    }

    button:hover .icon-open .chevron {
      opacity: 1;
      transform: translateX(0) scale(1);
    }

    .icon-close .sidebar-line {
      transform: translateX(-2px);
    }

    .icon-close .sidebar-rail {
      transform: scaleY(0.5);
    }

    .icon-close .chevron {
      opacity: 0;
      transform: translateX(-2px) scale(0.55);
    }

    button:hover .icon-close .sidebar-line {
      transform: translateX(0);
    }

    button:hover .icon-close .sidebar-rail {
      transform: scaleY(1);
    }

    button:hover .icon-close .chevron {
      opacity: 1;
      transform: translateX(0) scale(1);
    }

    @media (prefers-reduced-motion: reduce) {
      .sidebar-line,
      .sidebar-rail,
      .chevron {
        transition: none;
      }
    }

    @media (max-width: 799px) {
      :host {
        display: none;
      }
    }
  `;

  @property({ type: Boolean, reflect: true })
  collapsed = false;

  private animationFrame = 0;

  connectedCallback() {
    super.connectedCallback();
    this.collapsed = localStorage.getItem(STORAGE_KEY) === 'true';
    this.updateComplete.then(() => {
      this.applyChrome(this.collapsed);
      this.setDrawerWidth(this.collapsed ? WIDTH_COLLAPSED_PX : WIDTH_EXPANDED_PX);
    });
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
  }

  private getLayout(): HTMLElement | null {
    return this.closest('vaadin-app-layout');
  }

  private setDrawerWidth(width: number) {
    const layout = this.getLayout();
    if (!layout) {
      return;
    }
    const value = `${Math.round(width * 100) / 100}px`;
    layout.style.setProperty('--vaadin-app-layout-drawer-width', value);
    layout.style.setProperty('--vaadin-app-layout-drawer-offset-left', value);
    layout.style.setProperty('--_vaadin-app-layout-drawer-offset-size', value);
  }

  private applyChrome(collapsed: boolean) {
    const layout = this.getLayout();
    if (!layout) {
      return;
    }
    layout.classList.toggle(LAYOUT_CLASS, collapsed);
    layout.querySelectorAll('vaadin-side-nav-item').forEach((item) => {
      if (collapsed) {
        item.setAttribute('title', (item.textContent || '').trim());
      } else {
        item.removeAttribute('title');
      }
    });
    localStorage.setItem(STORAGE_KEY, String(collapsed));
  }

  private animateTo(collapsed: boolean) {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
    }
    const from = collapsed ? WIDTH_EXPANDED_PX : WIDTH_COLLAPSED_PX;
    const to = collapsed ? WIDTH_COLLAPSED_PX : WIDTH_EXPANDED_PX;
    this.applyChrome(collapsed);

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this.setDrawerWidth(to);
      return;
    }

    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / DURATION_MS);
      const eased = 1 - Math.pow(1 - progress, 3);
      this.setDrawerWidth(from + (to - from) * eased);
      if (progress < 1) {
        this.animationFrame = requestAnimationFrame(tick);
      } else {
        this.animationFrame = 0;
        this.setDrawerWidth(to);
      }
    };
    this.animationFrame = requestAnimationFrame(tick);
  }

  private onToggle() {
    this.collapsed = !this.collapsed;
    this.animateTo(this.collapsed);
  }

  private renderIcon(open: boolean) {
    return html`
      <svg class="icon ${open ? 'icon-open' : 'icon-close'}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 18" aria-hidden="true">
        <g class="sidebar-line">
          <line x1="6.75" y1="6.25" x2="6.75" y2="11.75" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="1.5"></line>
          <line class="sidebar-rail" x1="6.75" y1="3.25" x2="6.75" y2="14.75" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="1.5"></line>
        </g>
        <rect x="1.75" y="3.25" width="14.5" height="11.5" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"></rect>
        <polyline class="chevron" points="${open ? '9.25,6.25 12.75,9 9.25,11.75' : '12.75,6.25 9.25,9 12.75,11.75'}" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"></polyline>
      </svg>
    `;
  }

  render() {
    const label = this.collapsed ? 'Expand navigation' : 'Collapse navigation';
    return html`
      <button type="button" aria-label=${label} aria-pressed=${String(this.collapsed)} title=${label} @click=${this.onToggle}>
        ${this.renderIcon(this.collapsed)}
      </button>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'drawer-rail-toggle': DrawerRailToggle;
  }
}
