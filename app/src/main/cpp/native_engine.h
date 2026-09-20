#pragma once
#include <memory>
#include <mutex>
#include <string>

namespace CheezieNative {
class Engine {
public:
    explicit Engine(const std::string& engineDirectory);
    ~Engine();
    bool setPosition(const std::string& fen);
    bool analyze(int depth, int movetimeMs, int threads, int hashMb, int multiPv);
    void stop();
    std::string bestMove() const;
    std::string principalVariation() const;
    int scoreCp() const;
    int mate() const;
    int depth() const;
private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    mutable std::mutex mutex_;
};
}
