import JejakCore
import SwiftUI

/// Named places such as Home and Office. Stops inside a place's circle show its name on the timeline.
struct PlacesScreen: View {
    @Environment(Store.self) private var store
    @Environment(Recorder.self) private var recorder
    @State private var editing: PlaceEdit?
    @State private var latest: Fix?

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if store.isSample { SampleBanner() }
                if store.places.isEmpty {
                    Card {
                        EmptyMessage(icon: "mappin.and.ellipse", title: "No places yet",
                                     message: "Name places like Home or Office, and stops there show the name on your timeline. You can also name a stop from the timeline.")
                    }
                } else {
                    Card(padding: 6) {
                        ForEach(Array(store.places.enumerated()), id: \.element.id) { k, p in
                            Button {
                                editing = PlaceEdit(place: p)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "mappin")
                                        .foregroundStyle(.white)
                                        .frame(width: 36, height: 36)
                                        .background(Theme.accent, in: Circle())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(p.name).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                                        Text(Fmt.coords(p.lat, p.lon) + " · within " + Fmt.distance(p.radius))
                                            .font(.footnote)
                                            .foregroundStyle(Theme.muted)
                                            .monospacedDigit()
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.line)
                                }
                                .padding(10)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if k < store.places.count - 1 { Divider().padding(.leading, 58) }
                        }
                    }
                }
                Button {
                    if let f = latest { editing = PlaceEdit(place: nil, lat: f.lat, lon: f.lon) }
                } label: {
                    Label("Add a place here", systemImage: "plus")
                }
                .buttonStyle(FilledButtonStyle())
                .disabled(latest == nil)
                Text(latest == nil
                     ? "Start recording on the Live page to add the place you are at."
                     : "Uses your newest reading: " + Fmt.coords(latest?.lat ?? 0, latest?.lon ?? 0))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
        }
        .background(Theme.page)
        .navigationTitle("Places")
        .inlineTitle()
        .sheet(item: $editing) { PlaceEditor(edit: $0) }
        .task(id: store.revision) {
            latest = store.isSample ? await store.sampleRecent().first : recorder.last
        }
        .onChange(of: recorder.last?.t) { _, _ in
            if !store.isSample { latest = recorder.last }
        }
    }
}

/// A place being added (no place yet) or changed.
struct PlaceEdit: Identifiable {
    let id = UUID()
    var place: Place?
    var lat = 0.0
    var lon = 0.0
}

struct PlaceEditor: View {
    let edit: PlaceEdit
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var radius = 100.0
    @State private var confirmDelete = false
    private let sizes: [Double] = [50, 100, 150, 250, 500]

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("Home, Office, Mum's house…", text: $name).words()
                }
                Section {
                    Picker("Counts as here within", selection: $radius) {
                        ForEach(sizes, id: \.self) { Text(Fmt.distance($0)).tag($0) }
                    }
                } footer: {
                    Text("Stops whose middle is this close get the place's name. Make it bigger for large places such as a campus or a mall.")
                }
                Section {
                    FactRow("Where", Fmt.coords(edit.place?.lat ?? edit.lat, edit.place?.lon ?? edit.lon))
                }
                if edit.place != nil {
                    Section {
                        Button("Delete this place", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(edit.place == nil ? "New place" : "Edit place")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let n = name.trimmingCharacters(in: .whitespaces)
                        if var p = edit.place {
                            p.name = n
                            p.radius = radius
                            store.save(p)
                        } else {
                            _ = store.newPlace(n, edit.lat, edit.lon, radius: radius)
                        }
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog("Delete this place?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let p = edit.place { store.deletePlace(p.id) }
                    dismiss()
                }
            } message: {
                Text("Your readings stay. Stops there are shown without a name again.")
            }
            .onAppear {
                name = edit.place?.name ?? ""
                radius = edit.place?.radius ?? 100
                if !sizes.contains(radius) { radius = 100 }
            }
        }
    }
}
