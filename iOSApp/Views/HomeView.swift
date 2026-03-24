import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: AppViewModel
    let startAction: () -> Void
    let quickStartAction: () -> Void
    @State private var showingBackendSettings = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                headerSection
                quickActionCard
                backendStatusCard
                filterCard
                searchSection
                candidateSection
                favoritesSection
                importCard
            }
            .padding(20)
        }
        .background(
            LinearGradient(
                colors: [Color(red: 0.98, green: 0.96, blue: 0.92), Color(red: 0.94, green: 0.97, blue: 0.95)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .navigationTitle("NomNom")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("连接设置") {
                    showingBackendSettings = true
                }
            }
        }
        .sheet(isPresented: $showingBackendSettings) {
            BackendSettingsView(baseURL: viewModel.baseURLText) { newURL in
                viewModel.updateBaseURL(newURL)
                Task { await viewModel.bootstrap() }
            } onCheck: {
                Task { await viewModel.checkBackendConnection() }
            }
        }
        .sheet(isPresented: $viewModel.showingImportCompletionSheet) {
            ImportCompletionView(viewModel: viewModel)
        }
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("今晚别再纠结吃什么")
                .font(.system(size: 32, weight: .bold, design: .rounded))
            Text("先按筛选搜一轮，系统会直接把最合适的几家放进 PK。大众点评链接也能先导进来。")
                .foregroundStyle(.secondary)
            Text("当前已搜到 \(viewModel.searchResults.count) 家，候选池里有 \(viewModel.candidateVenueIds.count) 家。")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var quickActionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("快速体验", systemImage: "sparkles")
                .font(.headline)
            Text("搜索结果会自动进入候选池。你只需要再点一次，就能直接开始今晚这轮 PK。")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button(viewModel.isLoading || viewModel.isVoting ? "准备中…" : "用当前结果直接开始 PK") {
                quickStartAction()
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isLoading || viewModel.isVoting)
        }
        .padding(18)
        .background(Color(red: 0.91, green: 0.95, blue: 0.90), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var backendStatusCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("后端状态", systemImage: "network")
                    .font(.headline)
                Spacer()
                if viewModel.isLoading {
                    ProgressView()
                }
            }
            Text(viewModel.baseURLText)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Circle()
                    .fill(connectionColor)
                    .frame(width: 10, height: 10)
                Text(viewModel.connectionLabel)
                    .font(.subheadline)
            }
            Button("刷新搜索与收藏") {
                Task { await viewModel.reloadHome() }
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.isLoading)
        }
        .padding(18)
        .background(Color.white.opacity(0.85), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var filterCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("筛选条件", systemImage: "slider.horizontal.3")
                .font(.headline)
            Text("筛选项由后端元数据驱动。每次搜索后，前几家推荐会自动加入 PK 候选。")
                .font(.footnote)
                .foregroundStyle(.secondary)

            if viewModel.filterMetadata == nil {
                ProgressView("正在加载可选项…")
            }

            HStack {
                filterMenu(title: "城区", value: viewModel.selectedDistrictLabel) {
                    Button("不限") { viewModel.selectDistrict(nil) }
                    ForEach(viewModel.districtOptions, id: \.self) { district in
                        Button(district) { viewModel.selectDistrict(district) }
                    }
                }

                filterMenu(title: "商圈", value: viewModel.selectedBusinessAreaLabel) {
                    Button("不限") { viewModel.selectBusinessArea(nil) }
                    ForEach(viewModel.businessAreaOptions, id: \.self) { businessArea in
                        Button(businessArea) { viewModel.selectBusinessArea(businessArea) }
                    }
                }
            }

            filterMenu(title: "排序方式", value: viewModel.selectedSortLabel) {
                ForEach(viewModel.sortOptions) { option in
                    Button(option.label) { viewModel.selectSort(option.value) }
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("菜系")
                    .font(.subheadline.bold())
                let columns = [GridItem(.adaptive(minimum: 92), spacing: 8)]
                LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                    ForEach(viewModel.categoryOptions, id: \.self) { category in
                        let isSelected = viewModel.filters.selectedCategories.contains(category)
                        Button(category) {
                            viewModel.toggleCategorySelection(category)
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity)
                        .background(
                            (isSelected ? Color(red: 0.89, green: 0.94, blue: 1.0) : Color(.secondarySystemBackground)),
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(isSelected ? Color.blue.opacity(0.35) : Color.clear, lineWidth: 1)
                        )
                        .foregroundStyle(isSelected ? Color.blue : Color.primary)
                    }
                }
                Text(viewModel.filters.selectedCategories.isEmpty ? "不选菜系时会搜索全部分类。" : "当前已选 \(viewModel.filters.selectedCategories.count) 个菜系。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("预算与范围")
                    .font(.subheadline.bold())
                Stepper(
                    "最低人均 ¥\(viewModel.filters.priceMin)",
                    value: Binding(
                        get: { viewModel.filters.priceMin },
                        set: { viewModel.filters.priceMin = min($0, viewModel.filters.priceMax) }
                    ),
                    in: viewModel.priceRange.min...viewModel.filters.priceMax,
                    step: viewModel.priceRange.step
                )
                Stepper(
                    "最高人均 ¥\(viewModel.filters.priceMax)",
                    value: Binding(
                        get: { viewModel.filters.priceMax },
                        set: { viewModel.filters.priceMax = max($0, viewModel.filters.priceMin) }
                    ),
                    in: viewModel.filters.priceMin...viewModel.priceRange.max,
                    step: viewModel.priceRange.step
                )
                HStack {
                    filterMenu(title: "搜索半径", value: "\(viewModel.filters.radiusMeters)m") {
                        ForEach(viewModel.radiusOptions, id: \.self) { radius in
                            Button("\(radius)m") { viewModel.selectRadius(radius) }
                        }
                    }
                    filterMenu(title: "评分下限", value: String(format: "%.1f 分", viewModel.filters.ratingMin)) {
                        ForEach(viewModel.ratingOptions, id: \.self) { rating in
                            Button(String(format: "%.1f 分", rating)) { viewModel.selectRating(rating) }
                        }
                    }
                }
                filterMenu(title: "用餐人数", value: "\(viewModel.filters.peopleCount) 人") {
                    ForEach(viewModel.peopleCountOptions, id: \.self) { peopleCount in
                        Button("\(peopleCount) 人") { viewModel.selectPeopleCount(peopleCount) }
                    }
                }
                Toggle("只看当前营业", isOn: $viewModel.filters.openNow)
            }

            Button(viewModel.isLoading ? "搜索中…" : "按这些条件搜索") {
                Task { await viewModel.reloadHome() }
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isLoading)
        }
        .padding(18)
        .background(Color.white.opacity(0.88), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var importCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("导入大众点评", systemImage: "square.and.arrow.down")
                .font(.headline)
            Text("先把分享文本或店铺链接粘进来。能自动识别就直接入收藏，识别不全再补字段。")
                .font(.footnote)
                .foregroundStyle(.secondary)
            TextEditor(text: $viewModel.shareImportText)
                .frame(minHeight: 96)
                .padding(8)
                .background(Color(red: 0.97, green: 0.95, blue: 0.91), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            Button(viewModel.isImporting ? "导入中…" : "导入到收藏") {
                Task { await viewModel.importSharedText() }
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.isImporting)
        }
        .padding(18)
        .background(Color(red: 0.98, green: 0.93, blue: 0.86), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var favoritesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("我的收藏", systemImage: "heart.fill")
                    .font(.headline)
                Spacer()
                Text("\(viewModel.favorites.count) 家")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if viewModel.favorites.isEmpty {
                emptyState("还没有收藏，先从分享导入或搜索结果里加一批。")
            } else {
                ForEach(viewModel.favorites) { item in
                    CandidateRow(
                        venue: viewModel.candidateVenue(for: item.venueId) ?? CandidateVenue(
                            id: item.venueId,
                            name: item.name,
                            category: "收藏餐厅",
                            subtitle: "来自我的收藏",
                            avgPrice: item.avgPrice,
                            rating: item.rating,
                            distanceMeters: item.distanceMeters,
                            openStatus: "UNKNOWN",
                            sourceProvider: viewModel.sourceProviderLabel(for: item.sourceProvider ?? "DIANPING"),
                            note: item.note
                        ),
                        isSelected: viewModel.isSelected(item.venueId),
                        selectTitle: viewModel.isSelected(item.venueId) ? "移出候选" : "加入候选",
                        favoriteAction: nil,
                        selectAction: { viewModel.toggleCandidate(item.venueId) }
                    )
                }
            }
        }
    }

    private var searchSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("搜索结果", systemImage: "fork.knife")
                    .font(.headline)
                Spacer()
                Text("\(viewModel.searchResults.count) 家")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let searchStrategyLabel = viewModel.searchStrategyLabel {
                Text(searchStrategyLabel)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(strategyTint.opacity(0.12), in: Capsule())
                    .foregroundStyle(strategyTint)
            }
            if let searchNotice = viewModel.searchNotice, !searchNotice.isEmpty {
                Text(searchNotice)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let searchSelectionHint = viewModel.searchSelectionHint {
                Text(searchSelectionHint)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(14)
                    .background(Color(red: 0.90, green: 0.95, blue: 1.0), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            if viewModel.searchResults.isEmpty {
                emptyState("当前没有搜索结果，调整筛选条件后刷新试试。")
            } else {
                HStack(spacing: 10) {
                    Button(action: quickStartAction) {
                        Text("直接开始两两 PK")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!viewModel.canStartFromSearchResults)

                    Button("恢复前 \(viewModel.suggestedCandidateCount) 家推荐") {
                        viewModel.fillCandidatesFromSearchResults()
                    }
                    .buttonStyle(.bordered)
                    .disabled(!viewModel.canFillCandidatesFromSearchResults)
                }
                ForEach(viewModel.searchResults) { item in
                    CandidateRow(
                        venue: CandidateVenue(
                            id: item.venueId,
                            name: item.name,
                            category: item.subcategory,
                            subtitle: "\(item.district) · \(item.businessArea)",
                            avgPrice: item.avgPrice,
                            rating: item.rating,
                            distanceMeters: item.distanceMeters,
                            openStatus: item.openStatus,
                            sourceProvider: viewModel.sourceProviderLabel(for: item.sourceProvider),
                            note: nil
                        ),
                        isSelected: viewModel.isSelected(item.venueId),
                        selectTitle: viewModel.isSelected(item.venueId) ? "先别考虑" : "重新纳入",
                        favoriteAction: item.isInFavorites ? nil : { Task { await viewModel.addToFavorites(item.venueId) } },
                        selectAction: { viewModel.toggleCandidate(item.venueId) }
                    )
                }
            }
        }
    }

    private var candidateSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("本轮 PK 候选", systemImage: "square.stack.3d.up.fill")
                    .font(.headline)
                Spacer()
                Text("\(viewModel.candidateVenueIds.count)/32")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let searchSelectionHint = viewModel.searchSelectionHint {
                Text(searchSelectionHint + " 不想吃的可以在上面点“先别考虑”，也可以从收藏里补进来。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if viewModel.selectedCandidates.isEmpty {
                emptyState("搜索后会自动加入推荐结果，也可以从收藏里补 2 到 32 家开始今晚的 PK。")
            } else {
                ForEach(viewModel.selectedCandidates) { venue in
                    CandidateRow(
                        venue: venue,
                        isSelected: true,
                        selectTitle: "移出候选",
                        favoriteAction: nil,
                        selectAction: { viewModel.toggleCandidate(venue.id) }
                    )
                }
            }
            Button(action: startAction) {
                Text("开始两两 PK")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.canStartDecision)
        }
        .padding(18)
        .background(Color(red: 0.90, green: 0.95, blue: 0.91), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func filterMenu<Content: View>(title: String, value: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.subheadline.bold())
            Menu {
                content()
            } label: {
                HStack {
                    Text(value)
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(12)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    private func emptyState(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var connectionColor: Color {
        switch viewModel.connectionState {
        case .idle:
            return .gray
        case .checking:
            return .orange
        case .connected:
            return Color(red: 0.17, green: 0.60, blue: 0.35)
        case .failed:
            return .red
        }
    }

    private var strategyTint: Color {
        switch viewModel.searchStrategy {
        case "REMOTE_AMAP", "REMOTE_TENCENT", "REMOTE_BAIDU":
            return .green
        case "LOCAL_CACHE":
            return .orange
        default:
            return .secondary
        }
    }
}

private struct CandidateRow: View {
    let venue: CandidateVenue
    let isSelected: Bool
    let selectTitle: String
    let favoriteAction: (() -> Void)?
    let selectAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(venue.name)
                        .font(.headline)
                    Text("\(venue.category) · \(venue.subtitle)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("¥\(venue.avgPrice)")
                        .font(.subheadline.bold())
                    Text(String(format: "%.1f 分", venue.rating))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if let distance = venue.distanceMeters {
                        Text("\(distance)m")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            HStack(spacing: 8) {
                statusChip(title: venue.openStatus == "OPEN" ? "营业中" : venue.openStatus == "CLOSED" ? "已打烊" : "状态未知", tint: venue.openStatus == "OPEN" ? .green : .orange)
                statusChip(title: venue.sourceProvider, tint: .blue)
                if let note = venue.note, !note.isEmpty {
                    statusChip(title: note, tint: .brown)
                }
            }

            HStack {
                if let favoriteAction {
                    Button("加入收藏", action: favoriteAction)
                        .buttonStyle(.bordered)
                }
                Spacer()
                if isSelected {
                    Button(selectTitle, action: selectAction)
                        .buttonStyle(.bordered)
                } else {
                    Button(selectTitle, action: selectAction)
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func statusChip(title: String, tint: Color) -> some View {
        Text(title)
            .font(.caption)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12), in: Capsule())
            .foregroundStyle(tint)
    }
}

private struct ImportCompletionView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section("手动补全导入信息") {
                    TextField("店名", text: $viewModel.importDraft.name)
                    TextField("分类", text: $viewModel.importDraft.category)
                    TextField("人均价格", text: $viewModel.importDraft.avgPriceText)
                        .keyboardType(.numberPad)
                    TextField("城区", text: $viewModel.importDraft.district)
                    TextField("商圈", text: $viewModel.importDraft.businessArea)
                    TextField("地址", text: $viewModel.importDraft.address)
                    TextField("标签（逗号分隔）", text: $viewModel.importDraft.tagsText)
                }
            }
            .navigationTitle("补全餐厅")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") {
                        viewModel.cancelImportCompletion()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(viewModel.isImporting ? "保存中…" : "保存") {
                        Task {
                            await viewModel.completePendingImport()
                            if !viewModel.showingImportCompletionSheet {
                                dismiss()
                            }
                        }
                    }
                    .disabled(viewModel.isImporting)
                }
            }
        }
    }
}

private struct BackendSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draftURL: String
    let onSave: (String) -> Void
    let onCheck: () -> Void

    init(baseURL: String, onSave: @escaping (String) -> Void, onCheck: @escaping () -> Void) {
        _draftURL = State(initialValue: baseURL)
        self.onSave = onSave
        self.onCheck = onCheck
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("后端地址") {
                    TextField("http://127.0.0.1:8081", text: $draftURL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    Text("Simulator 推荐：http://127.0.0.1:8081")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("连接设置")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("检测") {
                        onSave(draftURL)
                        onCheck()
                    }
                    Button("保存") {
                        onSave(draftURL)
                        dismiss()
                    }
                }
            }
        }
    }
}
