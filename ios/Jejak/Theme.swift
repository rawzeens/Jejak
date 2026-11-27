import JejakCore
import SwiftUI

/// Colours and small pieces shared by every screen (the same look as the Android app).
enum Theme {
    static let brand = Color(hex: "#1D4ED8")
    static let brandDark = Color(hex: "#1E3A8A")
    static let brandSoft = Color(hex: "#E0E7FF")
    static let accent = Color(hex: "#F97316")
    static let accentSoft = Color(hex: "#FFEDD5")
    static let ink = Color(hex: "#0F172A")
    static let muted = Color(hex: "#64748B")
    static let line = Color(hex: "#E2E8F0")
    static let page = Color(hex: "#F8FAFC")
    static let good = Color(hex: "#16A34A")
    static let goodSoft = Color(hex: "#DCFCE7")
    static let bad = Color(hex: "#DC2626")
    static let badSoft = Color(hex: "#FEE2E2")
    static let warn = Color(hex: "#B45309")
    static let warnSoft = Color(hex: "#FEF3C7")
    static let greySoft = Color(hex: "#F1F5F9")
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt64(s, radix: 16) ?? 0x1D4ED8
        self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }
}

/// White rounded card with a thin outline.
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.line))
    }
}

struct Pill: View {
    let text: String
    var fg: Color = Theme.brand
    var bg: Color = Theme.brandSoft

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(fg)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(bg, in: Capsule())
    }
}

struct SectionTitle: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.semibold))
            .tracking(0.6)
            .foregroundStyle(Theme.muted)
            .padding(.top, 18)
            .padding(.bottom, 6)
            .padding(.leading, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct EmptyMessage: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title)
                .foregroundStyle(Theme.brand)
                .frame(width: 64, height: 64)
                .background(Theme.brandSoft, in: Circle())
            Text(title).font(.headline).padding(.top, 6)
            Text(message).font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}

/// A warning or tip with an icon, in a soft colour.
struct Notice<Actions: View>: View {
    let icon: String
    let title: String
    let message: String
    var fg: Color = Theme.warn
    var bg: Color = Theme.warnSoft
    @ViewBuilder var actions: Actions

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.headline)
                .foregroundStyle(fg)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                Text(message).font(.footnote).foregroundStyle(Theme.ink.opacity(0.8)).fixedSize(horizontal: false, vertical: true)
                actions.padding(.top, 2)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(bg, in: RoundedRectangle(cornerRadius: 14))
    }
}

extension Notice where Actions == EmptyView {
    init(icon: String, title: String, message: String, fg: Color = Theme.warn, bg: Color = Theme.warnSoft) {
        self.icon = icon
        self.title = title
        self.message = message
        self.fg = fg
        self.bg = bg
        self.actions = EmptyView()
    }
}

/// A label on the left and a value on the right.
struct FactRow: View {
    let label: String
    let value: String

    init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(Theme.muted)
            Spacer(minLength: 12)
            Text(value).foregroundStyle(Theme.ink).monospacedDigit().multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
        .padding(.vertical, 6)
    }
}

/// A small number with a label under it (the day's distance, time moving…).
struct Stat: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.headline).monospacedDigit().foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.caption).foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// "Sample days" strip shown while the made-up readings are on.
struct SampleBanner: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "wand.and.stars")
            Text("Sample days: made-up readings to try the app. Turn them off in Settings.")
                .fixedSize(horizontal: false, vertical: true)
        }
        .font(.footnote.weight(.medium))
        .foregroundStyle(Theme.accent)
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct FilledButtonStyle: ButtonStyle {
    var color: Color = Theme.brand

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(color.opacity(configuration.isPressed ? 0.8 : 1), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct OutlineButtonStyle: ButtonStyle {
    var color: Color = Theme.brand

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(color)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color.white.opacity(configuration.isPressed ? 0.6 : 1), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.line))
    }
}

extension Timeline.Mode {
    var symbol: String {
        switch self {
        case .walk: return "figure.walk"
        case .cycle: return "bicycle"
        case .vehicle: return "car.fill"
        case .flight: return "airplane"
        }
    }
}

// Small differences between iPhone and the Mac build used for checking.
extension View {
    func inlineTitle() -> some View {
        #if os(iOS)
        return navigationBarTitleDisplayMode(.inline)
        #else
        return self
        #endif
    }

    func words() -> some View {
        #if os(iOS)
        return textInputAutocapitalization(.words)
        #else
        return self
        #endif
    }
}

/// Opens the phone's Settings page for this app (to change location access).
func openAppSettings() {
    #if os(iOS)
    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    #endif
}
