import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';

@Component({
  selector: 'app-knowledge-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './knowledge-menu.html',
  styleUrl: './knowledge-menu.scss',
})
export default class KnowledgeMenu {}
