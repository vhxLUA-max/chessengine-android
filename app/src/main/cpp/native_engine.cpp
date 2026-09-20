#include "native_engine.h"

#include <algorithm>
#include <condition_variable>
#include <filesystem>
#include <mutex>
#include <sstream>
#include <vector>
#include "engine.h"
#include "uci.h"

namespace {
struct AnalysisState {
    std::mutex mutex;
    std::condition_variable cv;
    bool finished = false;
    std::string bestMove;
    std::string pv;
    int scoreCp = 0;
    int mate = 0;
    int depth = 0;
};
std::string joinPv(const Stockfish::Engine::InfoFull& info) {
    std::ostringstream out;
    bool first = true;
    for (const auto& move : info.pv) {
        if (!first) out << ' ';
        first = false;
        out << Stockfish::UCIEngine::move(move);
    }
    return out.str();
}
}
namespace CheezieNative {
struct Engine::Impl {
    std::unique_ptr<Stockfish::Engine> engine;
    AnalysisState state;
};
Engine::Engine(const std::string& engineDirectory) : impl_(std::make_unique<Impl>()) {
    impl_->engine = std::make_unique<Stockfish::Engine>(std::filesystem::path(engineDirectory));
    impl_->engine->set_on_update_full([this](const Stockfish::Engine::InfoFull& info) {
        std::lock_guard<std::mutex> lock(impl_->state.mutex);
        impl_->state.depth = int(info.depth);
        impl_->state.pv = joinPv(info);
    });
    impl_->engine->set_on_bestmove([this](std::string_view bestmove, std::string_view) {
        std::lock_guard<std::mutex> lock(impl_->state.mutex);
        impl_->state.bestMove = std::string(bestmove);
        impl_->state.finished = true;
        impl_->state.cv.notify_all();
    });
}
Engine::~Engine() {
    if (impl_ && impl_->engine) {
        impl_->engine->stop();
        impl_->engine->wait_for_search_finished();
    }
}
bool Engine::setPosition(const std::string& fen) {
    std::lock_guard<std::mutex> lock(mutex_);
    impl_->engine->stop();
    impl_->engine->wait_for_search_finished();
    return !impl_->engine->set_position(fen, {}).has_value();
}
bool Engine::analyze(int depth, int movetimeMs, int threads, int hashMb, int multiPv) {
    std::lock_guard<std::mutex> lock(mutex_);
    impl_->engine->stop();
    impl_->engine->wait_for_search_finished();
    auto& options = impl_->engine->get_options();
    options["Threads"] = std::max(1, threads);
    options["Hash"] = std::max(1, hashMb);
    options["MultiPV"] = std::max(1, multiPv);
    {
        std::lock_guard<std::mutex> stateLock(impl_->state.mutex);
        impl_->state.finished = false;
        impl_->state.bestMove.clear();
        impl_->state.pv.clear();
        impl_->state.depth = 0;
    }
    Stockfish::Search::LimitsType limits;
    limits.depth = depth > 0 ? depth : Stockfish::DEPTH_MAX;
    if (movetimeMs > 0) limits.movetime = movetimeMs;
    impl_->engine->go(limits);
    std::unique_lock<std::mutex> stateLock(impl_->state.mutex);
    impl_->state.cv.wait(stateLock, [this] { return impl_->state.finished; });
    return !impl_->state.bestMove.empty();
}
void Engine::stop() {
    if (impl_ && impl_->engine) impl_->engine->stop();
}
std::string Engine::bestMove() const {
    std::lock_guard<std::mutex> lock(impl_->state.mutex); return impl_->state.bestMove;
}
std::string Engine::principalVariation() const {
    std::lock_guard<std::mutex> lock(impl_->state.mutex); return impl_->state.pv;
}
int Engine::scoreCp() const {
    std::lock_guard<std::mutex> lock(impl_->state.mutex); return impl_->state.scoreCp;
}
int Engine::mate() const {
    std::lock_guard<std::mutex> lock(impl_->state.mutex); return impl_->state.mate;
}
int Engine::depth() const {
    std::lock_guard<std::mutex> lock(impl_->state.mutex); return impl_->state.depth;
}
}
