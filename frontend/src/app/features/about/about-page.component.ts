import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { interval } from 'rxjs';
import { AppLocationsComponent } from '../../shared/components/app-locations/app-locations.component';
import {
  buildPublicMediaUsageScopeKey,
  PublicMediaDisplayImage,
  PublicMediaService,
  PublicMediaUsageCollectionMap,
} from '../../core/services/public-media.service';
import {
  LinkedInPost,
  LinkedInPostService,
} from '../../core/services/linkedin-post.service';

const EMPTY_MEDIA_COLLECTIONS: PublicMediaUsageCollectionMap = {};
const LINKEDIN_SLIDE_INTERVAL_MS = 30_000;
const LINKEDIN_FALLBACK_POST_COUNT = 2;

type MemberId = 'joe' | 'matteo';
type PassionId =
  | 'bike-trial'
  | 'mountain'
  | 'ski'
  | 'self-hosting'
  | 'print-3d'
  | 'travel'
  | 'snowboard'
  | 'snowboard-instructor'
  | 'electronics'
  | 'woodworking'
  | 'van-life'
  | 'coffee'
  | 'cooking'
  | 'software-development';

interface PassionChip {
  id: PassionId;
  labelKey: string;
}

@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule, AppLocationsComponent],
  templateUrl: './about-page.component.html',
  styleUrl: './about-page.component.scss',
})
export class AboutPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly publicMediaService = inject(PublicMediaService);
  private readonly linkedInPostService = inject(LinkedInPostService);
  private readonly mediaByUsage = toSignal(
    this.publicMediaService.getUsageCollections([
      {
        usageType: 'ABOUT_MEMBER',
        usageKey: 'joe',
      },
      {
        usageType: 'ABOUT_MEMBER',
        usageKey: 'matteo',
      },
    ]),
    { initialValue: EMPTY_MEDIA_COLLECTIONS },
  );

  readonly joeImage = computed<PublicMediaDisplayImage | null>(() => {
    const image = this.publicMediaService.pickPrimaryUsage(
      this.mediaByUsage()[
        buildPublicMediaUsageScopeKey('ABOUT_MEMBER', 'joe')
      ] ?? [],
    );
    return image ? this.publicMediaService.toDisplayImage(image, 'card') : null;
  });

  readonly matteoImage = computed<PublicMediaDisplayImage | null>(() => {
    const image = this.publicMediaService.pickPrimaryUsage(
      this.mediaByUsage()[
        buildPublicMediaUsageScopeKey('ABOUT_MEMBER', 'matteo')
      ] ?? [],
    );
    return image ? this.publicMediaService.toDisplayImage(image, 'card') : null;
  });

  private readonly syncedLinkedInPosts = toSignal(
    this.linkedInPostService.getPosts(),
    { initialValue: [] as readonly LinkedInPost[] },
  );
  readonly activeLinkedInSlide = signal(0);
  readonly linkedInManuallyPaused = signal(false);
  readonly linkedInInteractionPaused = signal(false);
  readonly linkedInSlideCount = computed(
    () => this.syncedLinkedInPosts().length || LINKEDIN_FALLBACK_POST_COUNT,
  );
  readonly linkedInSlideIndexes = computed(() =>
    Array.from({ length: this.linkedInSlideCount() }, (_, index) => index),
  );
  readonly normalizedLinkedInSlide = computed(
    () => this.activeLinkedInSlide() % this.linkedInSlideCount(),
  );
  readonly activeLinkedInPost = computed<LinkedInPost | null>(() => {
    const posts = this.syncedLinkedInPosts();
    if (posts.length === 0) {
      return null;
    }
    return posts[this.normalizedLinkedInSlide()] ?? null;
  });
  readonly linkedInPaused = computed(
    () => this.linkedInManuallyPaused() || this.linkedInInteractionPaused(),
  );

  selectedMember: MemberId | null = null;
  hoveredMember: MemberId | null = null;

  readonly passions: ReadonlyArray<PassionChip> = [
    { id: 'mountain', labelKey: 'ABOUT.PASSION_MOUNTAIN' },
    { id: 'coffee', labelKey: 'ABOUT.PASSION_COFFEE' },
    { id: 'bike-trial', labelKey: 'ABOUT.PASSION_BIKE_TRIAL' },
    { id: 'electronics', labelKey: 'ABOUT.PASSION_ELECTRONICS' },
    { id: 'travel', labelKey: 'ABOUT.PASSION_TRAVEL' },
    { id: 'woodworking', labelKey: 'ABOUT.PASSION_WOODWORKING' },
    { id: 'print-3d', labelKey: 'ABOUT.PASSION_PRINT_3D' },
    { id: 'ski', labelKey: 'ABOUT.PASSION_SKI' },
    {
      id: 'software-development',
      labelKey: 'ABOUT.PASSION_SOFTWARE_DEVELOPMENT',
    },
    { id: 'snowboard', labelKey: 'ABOUT.PASSION_SNOWBOARD' },
    { id: 'van-life', labelKey: 'ABOUT.PASSION_VAN_LIFE' },
    { id: 'self-hosting', labelKey: 'ABOUT.PASSION_SELF_HOSTING' },
    { id: 'cooking', labelKey: 'ABOUT.PASSION_COOKING' },
    {
      id: 'snowboard-instructor',
      labelKey: 'ABOUT.PASSION_SNOWBOARD_INSTRUCTOR',
    },
  ];

  constructor() {
    afterNextRender(() => {
      interval(LINKEDIN_SLIDE_INTERVAL_MS)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          if (!this.linkedInPaused()) {
            this.nextLinkedInSlide();
          }
        });
    });
  }

  private readonly memberPassions: Readonly<
    Record<MemberId, ReadonlyArray<PassionId>>
  > = {
    joe: [
      'bike-trial',
      'mountain',
      'ski',
      'self-hosting',
      'print-3d',
      'travel',
      'coffee',
      'cooking',
      'software-development',
    ],
    matteo: [
      'bike-trial',
      'mountain',
      'snowboard',
      'snowboard-instructor',
      'electronics',
      'print-3d',
      'woodworking',
      'van-life',
    ],
  };

  get activeMember(): MemberId | null {
    return this.hoveredMember ?? this.selectedMember;
  }

  toggleSelectedMember(member: MemberId): void {
    this.selectedMember = this.selectedMember === member ? null : member;
  }

  setHoveredMember(member: MemberId | null): void {
    this.hoveredMember = member;
  }

  isMemberActive(member: MemberId): boolean {
    return this.activeMember === member;
  }

  isMemberSelected(member: MemberId): boolean {
    return this.selectedMember === member;
  }

  isPassionActive(passionId: PassionId): boolean {
    const member = this.activeMember;
    return member !== null && this.memberPassions[member].includes(passionId);
  }

  selectLinkedInSlide(index: number): void {
    this.activeLinkedInSlide.set(index % this.linkedInSlideCount());
  }

  nextLinkedInSlide(): void {
    this.selectLinkedInSlide(this.activeLinkedInSlide() + 1);
  }

  previousLinkedInSlide(): void {
    const count = this.linkedInSlideCount();
    this.selectLinkedInSlide(this.activeLinkedInSlide() - 1 + count);
  }

  toggleLinkedInPaused(): void {
    this.linkedInManuallyPaused.update((paused) => !paused);
  }

  setLinkedInInteractionPaused(paused: boolean): void {
    this.linkedInInteractionPaused.set(paused);
  }
}
