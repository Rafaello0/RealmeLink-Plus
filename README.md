# RealmeLink Plus

Локальні патчі Morphe для realme Link `com.realme.link` 5.8.811.2814.

## Склад
- Збереження вручну вибраного ANC / Transparency / Off
- Збереження Game Mode
- Оригінальні `AMOLED dark theme` та `Bypass session expiry` із hxreborn v1.80.0 (GPL-3.0-only)

Вихідний код hxreborn завантажується при складанні з зафіксованого коміту
`83839f596c9c7d8555c97809c34c2daabde03ec7`.
Всі функції залишаються окремими пунктами вибору в Morphe.

**Статус:** код BUILD002 проходить Java smoke-тест, але реальний `.mpp` та сценарії з фізичними навушниками ще потребують перевірки. Збірка має завершуватися помилкою, а не публікувати порожній артефакт.

## Збірка
- GitHub: **Actions → Build Morphe MPP → Run workflow**. Завантажити `.mpp` у Artifacts успішного запуску.
- Windows: PowerShell 7, Git, JDK 21. Виконати `BUILD.cmd`.
- Морфе: **Sources → + → Local** та вибрати `.mpp`.
- Непатчений оригінальний XAPK потрібної версії, а не APK з hxreborn, для повторного патчування.

Попередження: змінена підписом збірка може вимагати перевстановлення; зробіть резервну копію даних перед заміною. Не публікуйте вихідний APK або токени. Немає доступу до прошивки / OTA.

Аудит: `reports/RESEARCH.md`. Це незалежна модифікація, не пов'язана з realme чи Morphe.

## Ліцензія
Нові модифікації GPL-3.0-only; оригінальні патчі hxreborn також GPL-3.0-only. Зберігати copyright, LICENSE і NOTICE upstream.

## Доступ до GitHub Packages (якщо Gradle не знаходить Morphe)

Плагін Morphe `app.morphe.patches:2.1.0` поширюється через GitHub Packages.
Стандартний `GITHUB_TOKEN` приватного репозиторію може не мати доступу до пакетів чужої організації.
Створіть **Personal access token (classic)** тільки з правом `read:packages` у https://github.com/settings/tokens.
У GitHub репозиторію: **Settings → Secrets and variables → Actions → New repository secret**:

- Name: `MORPHE_PACKAGES_TOKEN`
- Secret: створений classic PAT (не пишіть його у чат, вихідний код чи логи).

Після збереження: **Actions → Build Morphe MPP → Run workflow**.
Успішний артефакт: `RealmeLink-Plus_build002_MPP` з файлом `RealmeLink-Plus_build002.mpp`.

Це не потрібно для **встановлення** готового `.mpp` у Morphe, лише для **складання**.


## Помилка «Метадані патча не знайдено»

**Важливо: використовуй Local, а не GitHub/Remote.**

1. На сторінці GitHub Actions вибери успішний Build Morphe MPP.
2. У розділі Artifacts завантаж ZIP `RealmeLink-Plus_build002_MPP`.
3. Розпакуй ZIP. Файл усередині має закінчуватися саме на `.mpp`.
4. Morphe → Sources → + → **Local** → вибери `RealmeLink-Plus_build002.mpp`.
5. Якщо раніше імпортував BUILD001, прибери це локальне джерело, тоді імпортуй BUILD002.

Репозиторій наразі приватний. Вставляння посилання
`https://github.com/Rafaello0/RealmeLink-Plus` до вкладки Remote не працює:
Morphe не має доступу до приватного репозиторію, і
у ньому не опубліковано `patches-bundle.json` та реліз із `.mpp`.
Успішний GitHub Actions artifact НЕ є Morphe Remote patch source.

BUILD001 хибно представлявся як `hxreborn’s patches v1.80.0` і містив
сотні нецільових патчів. BUILD002 виправляє назву пакета на
`RealmeLink Plus v0.2.0`, залишає тільки 4 патчі для realme Link,
а також автоматично перевіряє метадані локального `.mpp`.
