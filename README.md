# RealmeLink Plus

Локальні патчі Morphe для realme Link `com.realme.link` 5.8.811.2814.

## Склад
- Збереження вручну вибраного ANC / Transparency / Off
- Збереження Game Mode
- Оригінальні `AMOLED dark theme` та `Bypass session expiry` із hxreborn v1.80.0 (GPL-3.0-only)

Вихідний код hxreborn завантажується при складанні з зафіксованого коміту
`83839f596c9c7d8555c97809c34c2daabde03ec7`.
Всі функції залишаються окремими пунктами вибору в Morphe.

**Статус:** код BUILD001 проходить Java smoke-тест, але реальний `.mpp` та сценарії з фізичними навушниками ще потребують перевірки. Збірка має завершуватися помилкою, а не публікувати порожній артефакт.

## Збірка
- GitHub: **Actions → Build Morphe MPP → Run workflow**. Завантажити `.mpp` у Artifacts успішного запуску.
- Windows: PowerShell 7, Git, JDK 21. Виконати `BUILD.cmd`.
- Морфе: **Sources → + → Local** та вибрати `.mpp`.
- Непатчений оригінальний XAPK потрібної версії, а не APK з hxreborn, для повторного патчування.

Попередження: змінена підписом збірка може вимагати перевстановлення; зробіть резервну копію даних перед заміною. Не публікуйте вихідний APK або токени. Немає доступу до прошивки / OTA.

Аудит: `reports/RESEARCH.md`. Це незалежна модифікація, не пов'язана з realme чи Morphe.

## Ліцензія
Нові модифікації GPL-3.0-only; оригінальні патчі hxreborn також GPL-3.0-only. Зберігати copyright, LICENSE і NOTICE upstream.